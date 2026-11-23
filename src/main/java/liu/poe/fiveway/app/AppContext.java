package liu.poe.fiveway.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import liu.poe.fiveway.engine.ScriptEngine;
import liu.poe.fiveway.engine.StartResult;
import liu.poe.fiveway.hotkey.HotkeyService;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.input.KeySender;
import liu.poe.fiveway.input.MouseSender;
import liu.poe.fiveway.model.Buff;
import liu.poe.fiveway.model.KeyCombo;
import liu.poe.fiveway.model.Settings;
import liu.poe.fiveway.store.SettingsStore;
import liu.poe.fiveway.ui.UiHost;
import liu.poe.fiveway.ui.WindowManager;
import liu.poe.fiveway.update.UpdateService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 應用程式共用狀態 (全域唯一)：設定、服務、腳本執行狀態，以及「設定變更 → 通知 UI 刷新」。
 * 所有 UI 控制器都從這裡讀寫設定；設定一變更，已註冊的刷新回呼就會被呼叫。
 * 注意：{@link #update} 等方法只能在 JavaFX 執行緒呼叫。
 */
public final class AppContext {
    private static final Logger log = LoggerFactory.getLogger(AppContext.class);

    private static AppContext instance;

    public static AppContext get() {
        return instance;
    }

    public static AppContext create() {
        instance = new AppContext();
        return instance;
    }

    private final SettingsStore store = new SettingsStore();
    private final HotkeyService hotkeys = new HotkeyService();
    private final KeySender keySender = new KeySender();
    private final MouseSender mouseSender = new MouseSender();
    private final ScriptEngine engine = new ScriptEngine(keySender, mouseSender, hotkeys);
    private final UpdateService updates = new UpdateService();
    private final UpdateFlow updateFlow = new UpdateFlow(this);
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final List<Runnable> listeners = new ArrayList<>();
    private final ExecutorService background = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "app-background");
        t.setDaemon(true);
        return t;
    });

    private UiHost ui;
    private AppHost host;
    private WindowManager window;
    private boolean starting = false;
    private boolean pendingStop = false;

    private AppContext() {
        // 腳本執行狀態改變時，通知所有 UI 刷新 (按鈕停用狀態等)
        running.addListener((o, ov, nv) -> fireChanged());
    }

    // ── 取得服務 ───────────────────────────────────────────────────────
    public Settings settings() {
        return store.get();
    }

    public SettingsStore store() {
        return store;
    }

    public HotkeyService hotkeys() {
        return hotkeys;
    }

    public UpdateService updates() {
        return updates;
    }

    public UpdateFlow updateFlow() {
        return updateFlow;
    }

    public BooleanProperty runningProperty() {
        return running;
    }

    public boolean isRunning() {
        return running.get();
    }

    public ExecutorService background() {
        return background;
    }

    public UiHost ui() {
        return ui;
    }

    public void setUi(UiHost ui) {
        this.ui = ui;
    }

    public AppHost host() {
        return host;
    }

    public void setHost(AppHost host) {
        this.host = host;
    }

    public WindowManager window() {
        return window;
    }

    public void setWindow(WindowManager window) {
        this.window = window;
    }

    // ── 設定變更通知 ───────────────────────────────────────────────────
    /** 註冊「設定變更時刷新 UI」回呼 (UI 重新載入前請先 {@link #clearListeners()}) */
    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void clearListeners() {
        listeners.clear();
    }

    private void fireChanged() {
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }

    /** 修改設定 → 儲存 → 重新綁定快捷鍵 → 通知 UI 刷新 */
    public void update(Consumer<Settings> mutator) {
        mutator.accept(settings());
        store.save(false);
        rebindHotkeys();
        fireChanged();
    }

    /** 不通知 UI 的靜默修改 (視窗位置/大小這類高頻變動；不備份) */
    public void updateQuiet(Consumer<Settings> mutator) {
        mutator.accept(settings());
        store.save(true);
    }

    /** 重設所有設定 (保留視窗大小/位置與語言)，並重新套用視窗透明度/置頂 */
    public void resetSettings() {
        Settings s = store.reset();
        if (window != null) {
            window.setOpacity(s.opacity);
            window.setAlwaysOnTop(s.alwaysOnTop);
        }
        rebindHotkeys();
        fireChanged();
    }

    /** 註冊 toggle / 隱藏畫面 快捷鍵 (未設定或未啟用時解除綁定) */
    public void rebindHotkeys() {
        Settings s = settings();
        hotkeys.bindToggle(s.toggleHotkey, s.hotkeyMode, () -> Platform.runLater(this::toggleScript));
        hotkeys.bindHide(s.hideEnabled ? s.hideHotkey : null, () -> Platform.runLater(() -> {
            if (window != null) window.toggleVisibility();
        }));
    }

    // ── 按鍵重複檢查 ───────────────────────────────────────────────────
    /**
     * 檢查按鍵是否已被其它功能綁定。
     *
     * @param exclude 要排除的項目：toggle / hide / attackKey / enterKey / exitKey / riftKey / buff:&lt;id&gt;
     * @return 已綁定的功能名稱；未重複回傳 null
     */
    public String duplicateOf(KeyCombo k, String exclude) {
        if (k == null) return null;
        Settings s = settings();
        if (!"toggle".equals(exclude) && k.sameAs(s.toggleHotkey)) return I18n.t("dup.toggle");
        if (!"hide".equals(exclude) && k.sameAs(s.hideHotkey)) return I18n.t("dup.hide");
        if (!"attackKey".equals(exclude) && k.sameAs(s.attackKey)) return I18n.t("dup.attack");
        if (!"enterKey".equals(exclude) && k.sameAs(s.enterKey)) return I18n.t("dup.enter");
        if (!"exitKey".equals(exclude) && k.sameAs(s.exitKey)) return I18n.t("dup.exit");
        if (!"riftKey".equals(exclude) && k.sameAs(s.riftKey)) return I18n.t("dup.rift");
        for (Buff b : s.buffs) {
            if (("buff:" + b.id).equals(exclude)) continue;
            if (k.sameAs(b.key)) return I18n.t("dup.buff", b.name);
        }
        return null;
    }

    // ── 腳本啟動 / 停止 ────────────────────────────────────────────────
    /** 啟動檢查：回傳錯誤訊息；沒問題回傳 null */
    private String validate(Settings s) {
        // 啟動快捷鍵必須先設定，否則鼠標被頻繁移動時無法用滑鼠關閉
        if (s.toggleHotkey == null || !s.toggleHotkey.hasKeys()) return I18n.t("validate.toggleRequired");
        // 自動控制鼠標位置：需先綁定座標
        if (s.mouseControl && s.mousePos == null) return I18n.t("validate.mouseNeedsPos");
        // 至少選擇一種模式
        if (!s.attackerMode && !s.refreshMode && !s.buffMode) return I18n.t("validate.noMode");
        if (s.attackerMode && s.attackKey == null) return I18n.t("validate.attackNeedsKey");
        if (s.refreshMode) {
            if (s.enterKey == null) return I18n.t("validate.enterNeedsKey");
            if (s.exitKey == null) return I18n.t("validate.exitNeedsKey");
            if (s.refreshDirection == null) return I18n.t("validate.directionNeeded");
            // 時空裂隙重算：啟用時必須設定按鍵
            if (s.riftEnabled && s.riftKey == null) return I18n.t("validate.riftNeedsKey");
            // 雙人刷新模式：需同時啟用打手 + 刷新，並綁定兩個視窗座標
            if (s.dualRefreshMode && s.attackerMode) {
                if (s.dualAttackerPos == null) return I18n.t("validate.dualNeedsAttackerPos");
                if (s.dualAuraPos == null) return I18n.t("validate.dualNeedsAuraPos");
            }
        }
        if (s.buffMode) {
            boolean any = s.buffs.stream().anyMatch(Buff::usable);
            if (!any) return I18n.t("validate.buffNeedsUsable");
        }
        return null;
    }

    public void toggleScript() {
        if (running.get()) stopScript();
        else startScript();
    }

    public void startScript() {
        if (running.get() || starting) return;
        String err = validate(settings());
        if (err != null) {
            ui.showMessage(I18n.t("dialog.title.cannotStart"), err);
            return;
        }
        starting = true;
        pendingStop = false;
        Settings snapshot = settings().copy();
        background.execute(() -> {
            StartResult r = engine.start(snapshot);
            Platform.runLater(() -> {
                starting = false;
                if (!r.ok()) {
                    String msg = r.errorKey() != null ? I18n.t(r.errorKey())
                            : (r.errorDetail() != null ? r.errorDetail() : I18n.t("dialog.unknownError"));
                    ui.showMessage(I18n.t("dialog.title.startFailed"), msg);
                    return;
                }
                if (pendingStop) {
                    // 啟動過程中使用者已按下停止
                    pendingStop = false;
                    engine.stop();
                    return;
                }
                running.set(true);
            });
        });
    }

    public void stopScript() {
        if (starting) pendingStop = true;
        engine.stop();
        running.set(false);
    }

    /** 程式結束前的清理：停止腳本 (放開所有按鍵)、卸載鉤子、寫入設定 */
    public void shutdown() {
        log.info("程式結束，清理資源");
        try {
            engine.stop();
        } catch (Exception ignored) {
            // 略過
        }
        hotkeys.shutdown();
        store.flushNow();
    }
}
