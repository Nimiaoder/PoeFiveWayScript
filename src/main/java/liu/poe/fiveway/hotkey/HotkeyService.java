package liu.poe.fiveway.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import liu.poe.fiveway.model.KeyCombo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全域快捷鍵 (JNativeHook)：錄製、監聽、攔截。
 * <ul>
 *   <li>錄製：同一時間只允許一個 Session；開始新錄製時立即取消舊的</li>
 *   <li>toggle 快捷鍵：啟動/關閉腳本 (mode = listen 監聽 / intercept 攔截)</li>
 *   <li>hide 快捷鍵：隱藏/顯示畫面</li>
 * </ul>
 * 所有回呼都在原生鉤子執行緒上觸發，UI 層請自行 Platform.runLater。
 */
public final class HotkeyService {

    /** 錄製結果 */
    public record CaptureResult(boolean cancelled, String errorKey, List<String> keys, String display) {
        static CaptureResult cancelledResult() {
            return new CaptureResult(true, null, null, null);
        }

        static CaptureResult error(String key) {
            return new CaptureResult(false, key, null, null);
        }

        static CaptureResult ok(List<String> keys) {
            return new CaptureResult(false, null, keys, String.join("+", keys));
        }

        public KeyCombo toCombo() {
            return new KeyCombo(keys, display);
        }
    }

    private static final class CaptureSession {
        final Consumer<CaptureResult> callback;
        final Set<String> pressed = new LinkedHashSet<>();

        CaptureSession(Consumer<CaptureResult> callback) {
            this.callback = callback;
        }
    }

    private static final class Binding {
        final Set<String> keys;
        final String mode;
        final Runnable cb;

        Binding(Set<String> keys, String mode, Runnable cb) {
            this.keys = keys;
            this.mode = mode;
            this.cb = cb;
        }
    }

    /** 直接在原生執行緒執行事件 (攔截事件必須同步處理才有效) */
    private static final class DirectExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static Field reservedField;

    static {
        try {
            reservedField = NativeInputEvent.class.getDeclaredField("reserved");
            reservedField.setAccessible(true);
        } catch (Exception e) {
            reservedField = null;
        }
    }

    private boolean started = false;
    private volatile CaptureSession captureSession;
    private volatile Binding toggleBinding;
    private volatile Binding hideBinding;
    /** (type="down"|"up", keyName) */
    private volatile BiConsumer<String, String> scriptKeyListener;
    private final Set<String> heldMods = ConcurrentHashMap.newKeySet();
    private volatile int consumedKeyCode = -1;

    private final NativeKeyListener listener = new NativeKeyListener() {
        @Override
        public void nativeKeyPressed(NativeKeyEvent e) {
            try {
                onKeyDown(e);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void nativeKeyReleased(NativeKeyEvent e) {
            try {
                onKeyUp(e);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void nativeKeyTyped(NativeKeyEvent e) {
            // 不使用
        }
    };

    // ── 啟動鉤子 (只啟動一次) ─────────────────────────────────────────
    private synchronized void ensureStarted() {
        if (started) return;
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.WARNING);
            logger.setUseParentHandlers(false);
            GlobalScreen.setEventDispatcher(new DirectExecutor());
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(listener);
            started = true;
        } catch (NativeHookException e) {
            System.err.println("全域鍵盤鉤子載入失敗: " + e.getMessage());
        }
    }

    // ── 事件處理 ──────────────────────────────────────────────────────
    private void onKeyDown(NativeKeyEvent e) {
        String name = KeyNames.keyName(e.getKeyCode());
        if (KeyNames.MODIFIERS.contains(name)) heldMods.add(name);

        // 錄製 Session
        CaptureSession cs = captureSession;
        if (cs != null) {
            if ("Esc".equals(name)) {
                captureSession = null;
                cs.callback.accept(CaptureResult.cancelledResult());
                return;
            }
            cs.pressed.add(name);
            return;
        }

        // 觸發 toggle 快捷鍵
        Binding tb = toggleBinding;
        if (tb != null && matches(tb, name)) {
            tb.cb.run();
            if ("intercept".equals(tb.mode)) {
                // 攔截：阻止事件傳給遊戲
                consume(e);
                consumedKeyCode = e.getKeyCode();
            }
        }

        // 隱藏/顯示畫面快捷鍵
        Binding hb = hideBinding;
        if (hb != null && matches(hb, name)) hb.cb.run();

        BiConsumer<String, String> l = scriptKeyListener;
        if (l != null) l.accept("down", name);
    }

    private void onKeyUp(NativeKeyEvent e) {
        String name = KeyNames.keyName(e.getKeyCode());
        if (KeyNames.MODIFIERS.contains(name)) heldMods.remove(name);

        if (consumedKeyCode == e.getKeyCode()) {
            consume(e);
            consumedKeyCode = -1;
        }

        CaptureSession cs = captureSession;
        if (cs != null) {
            // 使用者放開鍵 → 完成錄製
            List<String> combo = normalizeCombo(new ArrayList<>(cs.pressed));
            if (combo.isEmpty()) return; // 尚未按任何鍵
            boolean hasNonMod = combo.stream().anyMatch(k -> !KeyNames.MODIFIERS.contains(k));
            captureSession = null;
            if (!hasNonMod) {
                // 不得單獨為 Ctrl/Shift/Alt
                cs.callback.accept(CaptureResult.error("recorder.error.modifierOnly"));
                return;
            }
            cs.callback.accept(CaptureResult.ok(combo));
            return;
        }

        BiConsumer<String, String> l = scriptKeyListener;
        if (l != null) l.accept("up", name);
    }

    private boolean matches(Binding b, String name) {
        Set<String> current = new LinkedHashSet<>(heldMods);
        if (!KeyNames.MODIFIERS.contains(name)) current.add(name);
        return current.equals(b.keys);
    }

    /** 依序：Ctrl, Shift, Alt, 其它 */
    private static List<String> normalizeCombo(List<String> keys) {
        List<String> out = new ArrayList<>();
        for (String m : KeyNames.MODIFIER_ORDER) {
            if (keys.contains(m)) out.add(m);
        }
        for (String k : keys) {
            if (!KeyNames.MODIFIERS.contains(k)) out.add(k);
        }
        return out;
    }

    /** 嘗試吃掉事件 (Windows 上有效；失敗則忽略，行為退化為「監聽」) */
    private static void consume(NativeInputEvent e) {
        if (reservedField == null) return;
        try {
            reservedField.setShort(e, (short) 0x01);
        } catch (Exception ignored) {
            // 略過
        }
    }

    // ── 對外 API ──────────────────────────────────────────────────────

    /** 開始錄製 (同一時間只有一個 session，舊的會立即收到 cancelled) */
    public void startCapture(Consumer<CaptureResult> callback) {
        ensureStarted();
        CaptureSession old = captureSession;
        captureSession = new CaptureSession(callback);
        if (old != null) old.callback.accept(CaptureResult.cancelledResult());
    }

    public void cancelCapture() {
        CaptureSession cs = captureSession;
        if (cs != null) {
            captureSession = null;
            cs.callback.accept(CaptureResult.cancelledResult());
        }
    }

    /** 綁定 toggle 快捷鍵；combo 為 null 則解除 */
    public void bindToggle(KeyCombo combo, String mode, Runnable cb) {
        if (combo == null || !combo.hasKeys()) {
            toggleBinding = null;
            return;
        }
        ensureStarted();
        toggleBinding = new Binding(new LinkedHashSet<>(combo.keys), mode, cb);
    }

    /** 綁定隱藏畫面快捷鍵；combo 為 null 則解除 */
    public void bindHide(KeyCombo combo, Runnable cb) {
        if (combo == null || !combo.hasKeys()) {
            hideBinding = null;
            return;
        }
        ensureStarted();
        hideBinding = new Binding(new LinkedHashSet<>(combo.keys), "listen", cb);
    }

    /** 供 ScriptEngine 註冊 (時空裂隙鍵監聽)；fn 為 null 則解除 */
    public void setScriptKeyListener(BiConsumer<String, String> fn) {
        scriptKeyListener = fn;
        if (fn != null) ensureStarted();
    }

    public synchronized void shutdown() {
        if (!started) return;
        try {
            GlobalScreen.removeNativeKeyListener(listener);
            GlobalScreen.unregisterNativeHook();
        } catch (Exception ignored) {
            // 略過
        }
        started = false;
    }
}
