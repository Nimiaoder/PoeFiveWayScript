package liu.poe.fiveway.engine;

import liu.poe.fiveway.config.AppConfig;
import liu.poe.fiveway.hotkey.HotkeyService;
import liu.poe.fiveway.input.KeySender;
import liu.poe.fiveway.input.MouseSender;
import liu.poe.fiveway.model.Buff;
import liu.poe.fiveway.model.KeyCombo;
import liu.poe.fiveway.model.ScreenPoint;
import liu.poe.fiveway.model.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 腳本狀態機：自動攻擊 + 自動刷新 (單人 / 雙人) + 自動 Buff 排程。
 * 停止時：立即取消所有 Timer / Wait、釋放所有按下的鍵。
 * <p>
 * 每次 start 會建立一個獨立的 {@link Session}，舊 Session 的殘留執行緒在 stop 後
 * 會因 running=false 而自動結束，不會影響新的 Session。
 */
public final class ScriptEngine {

    private static final String WINDOW_ATTACKER = "attacker";
    private static final String WINDOW_AURA = "aura";

    private final KeySender keys;
    private final MouseSender mouse;
    private final HotkeyService hotkeys;
    private volatile Session session;

    public ScriptEngine(KeySender keys, MouseSender mouse, HotkeyService hotkeys) {
        this.keys = keys;
        this.mouse = mouse;
        this.hotkeys = hotkeys;
    }

    public boolean isRunning() {
        Session s = session;
        return s != null && s.running;
    }

    /** 啟動腳本 (可能阻塞約數百毫秒，請勿在 UI 執行緒呼叫) */
    public synchronized StartResult start(Settings cfg) {
        if (isRunning()) return StartResult.failure("engine.error.alreadyRunning", null);
        Session s = new Session(cfg);
        session = s;
        try {
            s.begin();
            return StartResult.success();
        } catch (Exception e) {
            e.printStackTrace();
            s.shutdown();
            return StartResult.failure(null, e.getMessage());
        }
    }

    /** 停止腳本：立即取消所有 Timer / Wait，釋放所有按下的鍵 */
    public void stop() {
        Session s = session;
        if (s != null) s.shutdown();
    }

    // ══════════════════════════════════════════════════════════════════

    /** 讓 stop 能立即中斷等待用的例外 (不產生堆疊，開銷極低) */
    private static final class StopSignal extends RuntimeException {
        StopSignal() {
            super(null, null, false, false);
        }
    }

    private static final StopSignal STOP = new StopSignal();

    /** 可中斷 / 可重新計時的等待 */
    private static final class Wait {
        final long ms;
        final boolean resettable;
        long deadline;
        boolean cancelled;

        Wait(long ms, boolean resettable) {
            this.ms = ms;
            this.resettable = resettable;
        }
    }

    private record Step(List<String> keys, int waitMs) {
    }

    private final class Session {
        final Settings config;
        volatile boolean running = true;
        volatile String focusedWindow;               // 目前焦點視窗："attacker" / "aura" / null (未知)
        boolean riftListenerBound = false;

        final List<Wait> waits = new ArrayList<>();
        final List<List<String>> heldKeys = new CopyOnWriteArrayList<>();
        // 序列化所有按鍵動作 (Buff / Refresh 排隊，不並發送鍵)
        final ExecutorService actions = Executors.newSingleThreadExecutor(daemon("script-actions"));
        final ScheduledExecutorService timers = Executors.newScheduledThreadPool(1, daemon("script-timers"));
        Thread refreshThread;

        Session(Settings config) {
            this.config = config;
        }

        private java.util.concurrent.ThreadFactory daemon(String name) {
            return r -> {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            };
        }

        // ── 啟動 / 停止 ────────────────────────────────────────────────
        void begin() {
            // 時空裂隙按鍵監聽 (可開關)
            bindRiftListener();

            // 自動攻擊：先按下 Attack
            // 雙人模式：啟動時先把焦點移到「打手視窗」，避免攻擊/Buff 打到光環師視窗
            if (attackUsable()) {
                if (isDualMode()) focusWindow(config.dualAttackerPos, WINDOW_ATTACKER);
                holdAttack();
            }

            // 自動刷新
            if (config.refreshMode) {
                refreshThread = new Thread(isDualMode() ? this::dualRefreshLoop : this::refreshLoop, "script-refresh");
                refreshThread.setDaemon(true);
                refreshThread.start();
            }

            // 自動 Buff
            if (config.buffMode) scheduleBuffs();
        }

        void shutdown() {
            if (!running) {
                releaseAll();
                return;
            }
            running = false;
            unbindRiftListener();
            timers.shutdownNow();
            synchronized (waits) {
                for (Wait w : new ArrayList<>(waits)) {
                    synchronized (w) {
                        w.cancelled = true;
                        w.notifyAll();
                    }
                }
            }
            if (refreshThread != null) refreshThread.interrupt();
            for (Runnable r : actions.shutdownNow()) {
                if (r instanceof Future<?> f) f.cancel(true);
            }
            releaseAll();
        }

        /** 釋放所有按下的鍵 (含 Attack) */
        void releaseAll() {
            List<List<String>> toRelease = new ArrayList<>(heldKeys);
            heldKeys.clear();
            for (List<String> k : toRelease) {
                try {
                    keys.releaseKey(k);
                } catch (Exception ignored) {
                    // 略過
                }
            }
            // 保險：若 Attack 未在 heldKeys 中也再釋放一次
            if (config.attackKey != null && config.attackKey.hasKeys()) {
                try {
                    keys.releaseKey(config.attackKey.keys);
                } catch (Exception ignored) {
                    // 略過
                }
            }
        }

        // ── 等待 ───────────────────────────────────────────────────────

        /**
         * 可中斷 sleep。resettable = true 時，此等待可被「時空裂隙按鍵」重新計時 (歸零重算)。
         * 腳本被停止時丟出 {@link StopSignal} 讓呼叫端立即結束。
         */
        void sleep(long ms, boolean resettable) {
            if (!running) throw STOP;
            if (ms <= 0) return;
            Wait w = new Wait(ms, resettable);
            synchronized (waits) {
                waits.add(w);
            }
            try {
                synchronized (w) {
                    w.deadline = System.nanoTime() + ms * 1_000_000L;
                    while (!w.cancelled) {
                        long remaining = w.deadline - System.nanoTime();
                        if (remaining <= 0) break;
                        try {
                            w.wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw STOP;
                        }
                    }
                }
            } finally {
                synchronized (waits) {
                    waits.remove(w);
                }
            }
            if (!running) throw STOP;
        }

        /**
         * 時空裂隙按鍵：使用者手動按下時，把「進圈/出圈之間的等待」歸零重算。
         * 用途：獵首把玩家傳送走，手動歸位後原本的等待秒數會失準，按一下即可重新計時。
         */
        void resetPendingWaits() {
            List<Wait> targets = new ArrayList<>();
            synchronized (waits) {
                for (Wait w : waits) {
                    if (w.resettable) targets.add(w);
                }
            }
            if (targets.isEmpty()) return;
            for (Wait w : targets) {
                synchronized (w) {
                    w.deadline = System.nanoTime() + w.ms * 1_000_000L;
                    w.notifyAll();
                }
            }
            System.out.println("[rift] 時空裂隙鍵觸發，重算等待計時 (" + targets.size() + " 筆)");
        }

        void bindRiftListener() {
            if (!config.riftEnabled || config.riftKey == null || !config.riftKey.hasKeys()) return;
            List<String> k = config.riftKey.keys;
            String mainKey = k.get(k.size() - 1);
            hotkeys.setScriptKeyListener((type, name) -> {
                if (!running) return;
                if (!"down".equals(type)) return;
                if (!name.equals(mainKey)) return;
                resetPendingWaits();
            });
            riftListenerBound = true;
        }

        void unbindRiftListener() {
            if (!riftListenerBound) return;
            hotkeys.setScriptKeyListener(null);
            riftListenerBound = false;
        }

        // ── 按鍵動作 ───────────────────────────────────────────────────
        void markPress(List<String> k) {
            if (!running) throw STOP;
            heldKeys.add(k);
            keys.pressKey(k);
        }

        void markRelease(List<String> k) {
            heldKeys.remove(k);
            keys.releaseKey(k);
        }

        /** 序列化執行 (Buff / Refresh 動作排隊)；回傳的 Future 可等待該動作完成 */
        Future<?> enqueue(Runnable fn) {
            try {
                return actions.submit(() -> {
                    if (!running) return;
                    try {
                        fn.run();
                    } catch (StopSignal ignored) {
                        // 腳本已停止
                    } catch (Throwable t) {
                        if (running) t.printStackTrace();
                    }
                });
            } catch (RejectedExecutionException e) {
                return CompletableFuture.completedFuture(null);
            }
        }

        /** 等待排隊的動作完成；腳本停止時丟出 StopSignal */
        void await(Future<?> f) {
            try {
                while (true) {
                    if (!running) throw STOP;
                    try {
                        f.get(200, TimeUnit.MILLISECONDS);
                        return;
                    } catch (TimeoutException ignored) {
                        // 繼續等待
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw STOP;
            } catch (CancellationException e) {
                throw STOP;
            } catch (ExecutionException ignored) {
                // 動作內部例外已記錄
            }
        }

        /** 單純短按一個鍵 (按壓固定 HOLD_MS)；即使中途被停止也一定會放開 */
        void tapKey(List<String> k) {
            if (!running || k == null || k.isEmpty()) return;
            markPress(k);
            try {
                sleep(AppConfig.HOLD_MS, false);
            } finally {
                markRelease(k);
            }
        }

        // ── 攻擊鍵 ─────────────────────────────────────────────────────
        boolean attackUsable() {
            return config.attackerMode && config.attackKey != null && config.attackKey.hasKeys();
        }

        void holdAttack() {
            if (!attackUsable()) return;
            if (heldKeys.contains(config.attackKey.keys)) return;
            markPress(config.attackKey.keys);
        }

        void pauseAttack() {
            if (!attackUsable()) return;
            markRelease(config.attackKey.keys);
        }

        /** 其它按鍵流程：Attack Up → delay → key tap → delay → Attack Down */
        void executeWithAttackPause(List<String> k) {
            if (!running) return;
            int delay = config.attackResumeDelay;
            boolean useAttack = attackUsable();

            if (useAttack) pauseAttack();
            sleep(delay, false);
            if (!running) return;

            tapKey(k);

            sleep(delay, false);
            if (!running) return;
            if (useAttack) holdAttack();
        }

        // ── 單人 Refresh 主 loop ───────────────────────────────────────
        void refreshLoop() {
            try {
                List<Step> seq = Settings.DIR_ENTER_FIRST.equals(config.refreshDirection)
                        ? List.of(step(config.enterKey, config.enterWaitMs), step(config.exitKey, config.exitWaitMs))
                        : List.of(step(config.exitKey, config.exitWaitMs), step(config.enterKey, config.enterWaitMs));

                while (running) {
                    for (Step st : seq) {
                        if (!running) return;
                        // 進圈/出圈前：如啟用自動控制鼠標位置，先移動鼠標
                        if (config.mouseControl && config.mousePos != null) {
                            mouse.moveAbsolute(config.mousePos);
                        }
                        // Refresh 具最高優先，透過 enqueue 保證與 Buff 不衝突
                        await(enqueue(() -> executeWithAttackPause(st.keys())));
                        if (!running) return;
                        // 此等待可被時空裂隙鍵重算
                        sleep(st.waitMs(), true);
                    }
                }
            } catch (StopSignal ignored) {
                // 腳本已停止
            } catch (Throwable t) {
                if (running) t.printStackTrace();
            }
        }

        // ── 雙人 Refresh 主 loop ───────────────────────────────────────
        // 流程 (以「先進圈」為例)：
        //   打手視窗(移鼠標→右鍵 focus)→進圈 → 光環師視窗→進圈 → 回打手視窗→恢復攻擊
        //   → 等待 n 毫秒 (可被時空裂隙鍵重算)
        //   打手視窗→出圈 → 光環師視窗→出圈 → 回打手視窗→恢復攻擊 → 等待 n 毫秒 → loop
        // 重點：只有鼠標停在打手視窗時才送出攻擊鍵，避免把攻擊打到光環師視窗
        void focusWindow(ScreenPoint point, String which) {
            if (!running) return;
            // 記錄目前焦點視窗，讓 Buff 能判斷「現在是否在打手視窗」
            focusedWindow = which;
            mouse.moveAbsolute(point);
            if (config.dualRightClickFocus) {
                mouse.rightClick();
            }
            // 視窗切換 / focus 需要一點緩衝，否則按鍵會掉進舊視窗
            sleep(Math.max(0, config.dualFocusDelayMs), false);
        }

        /**
         * 單次雙人切窗流程 (進圈或出圈)：不含等待時間，
         * 等待放在 queue 外面，讓 Buff 有機會在「已回到打手視窗」的空檔施放
         */
        void dualPhase(List<String> k) {
            ScreenPoint attackerPos = config.dualAttackerPos;
            ScreenPoint auraPos = config.dualAuraPos;

            // 1) 離開打手視窗前先放開攻擊鍵
            pauseAttack();

            // 2) 打手視窗：focus → 送出按鍵
            focusWindow(attackerPos, WINDOW_ATTACKER);
            if (!running) return;
            tapKey(k);

            // 3) 光環師視窗：focus → 送出同一個按鍵
            focusWindow(auraPos, WINDOW_AURA);
            if (!running) return;
            tapKey(k);

            // 4) 回到打手視窗：focus → 盡可能持續攻擊
            focusWindow(attackerPos, WINDOW_ATTACKER);
            if (!running) return;
            holdAttack();
        }

        void dualRefreshLoop() {
            try {
                Step enterStep = step(config.enterKey, config.enterWaitMs);
                Step exitStep = step(config.exitKey, config.exitWaitMs);
                List<Step> seq = Settings.DIR_EXIT_FIRST.equals(config.refreshDirection)
                        ? List.of(exitStep, enterStep)
                        : List.of(enterStep, exitStep);

                while (running) {
                    for (Step st : seq) {
                        if (!running) return;
                        // 切窗流程整段排隊，避免 Buff 插在視窗切換中間造成按鍵打錯視窗
                        await(enqueue(() -> dualPhase(st.keys())));
                        if (!running) return;
                        // 等待放在排隊之外：此時焦點已回到打手視窗，
                        // 等待期間若有 Buff 到期，可立即安全施放 (可被時空裂隙鍵重算)
                        sleep(st.waitMs(), true);
                    }
                }
            } catch (StopSignal ignored) {
                // 腳本已停止
            } catch (Throwable t) {
                if (running) t.printStackTrace();
            }
        }

        private Step step(KeyCombo key, int waitMs) {
            List<String> k = (key == null || key.keys == null) ? Collections.emptyList() : key.keys;
            return new Step(k, waitMs);
        }

        /** 是否使用雙人刷新模式 (需同時勾選打手 + 刷新，並完成兩個座標綁定) */
        boolean isDualMode() {
            return config.dualRefreshMode
                    && config.attackerMode
                    && config.refreshMode
                    && config.dualAttackerPos != null
                    && config.dualAuraPos != null;
        }

        // ── Buff 排程 ──────────────────────────────────────────────────
        void scheduleBuffs() {
            for (Buff buff : config.buffs) {
                if (!buff.usable()) continue;
                final long intervalMs = Math.max(1, buff.intervalSec) * 1000L;

                Runnable cast = () -> {
                    if (!running) return;
                    // Buff 使用 enqueue 排隊，不與 Refresh / 其它 Buff 同時送鍵
                    enqueue(() -> {
                        // 雙人模式：Buff 只會在「打手視窗」施放。
                        // 因為切窗流程整段排隊，排到這裡時焦點必定已回到打手視窗，
                        // 也就是說：切到光環師視窗期間到期的 Buff 會「等待」而不是強制切窗打斷。
                        if (isDualMode()) {
                            if (!WINDOW_ATTACKER.equals(focusedWindow)) {
                                // 僅在焦點狀態未知 (例如剛啟動、尚未跑過切窗流程) 時才主動聚焦打手視窗
                                focusWindow(config.dualAttackerPos, WINDOW_ATTACKER);
                            }
                            if (!running) return;
                        }
                        executeWithAttackPause(buff.key.keys);
                    });
                };

                // 啟動立即施放
                if (buff.castOnStart) cast.run();

                Runnable tick = new Runnable() {
                    @Override
                    public void run() {
                        if (!running) return;
                        cast.run();
                        try {
                            timers.schedule(this, intervalMs, TimeUnit.MILLISECONDS);
                        } catch (RejectedExecutionException ignored) {
                            // 已停止
                        }
                    }
                };
                try {
                    timers.schedule(tick, intervalMs, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException ignored) {
                    // 已停止
                }
            }
        }
    }
}
