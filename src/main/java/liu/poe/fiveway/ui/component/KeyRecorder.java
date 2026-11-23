package liu.poe.fiveway.ui.component;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.hotkey.HotkeyService;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.KeyCombo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * 按鍵錄製元件 (對應原版 KeyRecorder)：
 * <ul>
 *   <li>點擊後進入錄製，放開按鍵即完成；Esc 取消；點擊元件外部也會取消</li>
 *   <li>全域同一時間只有一個元件在錄製；點擊已在錄製中的按鈕視為無效</li>
 *   <li>元件本身不保存變更：使用者錄製完成後呼叫 onChange，由外部決定要不要
 *       {@link #setValue(KeyCombo)} (例如按鍵重複時就不接受)</li>
 * </ul>
 */
public class KeyRecorder extends StackPane {

    /** 目前正在錄製的元件 (全域只允許一個 session) */
    private static KeyRecorder active;

    @FXML private Button keyBtn;
    @FXML private Button clearBtn;

    private KeyCombo value;
    private boolean recording;
    private Consumer<KeyCombo> onChange = v -> { };

    private final EventHandler<MouseEvent> outsideClick = this::onOutsideClick;

    public KeyRecorder() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/key_recorder.fxml"), I18n.bundle());
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // 阻止 Space / Enter 觸發按鈕
        keyBtn.addEventFilter(KeyEvent.ANY, Event::consume);

        ChangeListener<Boolean> disableListener = (o, ov, nv) -> updateUi();
        disabledProperty().addListener(disableListener);
        // 離開場景時(例如重新載入 UI)結束錄製
        sceneProperty().addListener((o, oldScene, newScene) -> {
            if (newScene == null && recording) {
                AppContext.get().hotkeys().cancelCapture();
                stopRecording();
            }
        });
        updateUi();
    }

    public KeyCombo getValue() {
        return value;
    }

    public void setValue(KeyCombo v) {
        this.value = v;
        updateUi();
    }

    public void setOnChange(Consumer<KeyCombo> onChange) {
        this.onChange = onChange != null ? onChange : v -> { };
    }

    private void updateUi() {
        String label;
        if (recording) label = I18n.t("recorder.recording");
        else if (value != null && value.display != null && !value.display.isEmpty()) label = value.display;
        else label = I18n.t("recorder.placeholder");
        keyBtn.setText(label);

        keyBtn.getStyleClass().removeAll("recording", "empty");
        if (recording) keyBtn.getStyleClass().add("recording");
        else if (value == null) keyBtn.getStyleClass().add("empty");

        boolean showClear = value != null && !recording && !isDisabled();
        clearBtn.setVisible(showClear);
        clearBtn.setManaged(showClear);
    }

    // ── 錄製流程 ───────────────────────────────────────────────────────
    @FXML
    private void onKeyBtnPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) return;
        start();
    }

    @FXML
    private void onClear() {
        if (isDisabled()) return;
        if (recording) {
            AppContext.get().hotkeys().cancelCapture();
            stopRecording();
        }
        onChange.accept(null);
    }

    private void start() {
        if (isDisabled()) return;
        // 已在錄製中：忽略再次點擊
        if (recording) return;
        try {
            Scene scene = getScene();
            if (scene == null) return;
            // 讓輸入框先失焦，避免錄製的按鍵同時寫入輸入框 (例如 Buff 名稱)
            scene.getRoot().requestFocus();

            if (active != null && active != this) active.stopRecording();
            active = this;
            recording = true;
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick);
            updateUi();
            AppContext.get().hotkeys().startCapture(r -> Platform.runLater(() -> onCaptured(r)));
        } catch (Exception ex) {
            stopRecording();
            AppContext.get().ui().showError(
                    ex.getMessage() != null ? ex.getMessage() : I18n.t("recorder.error.startFailed"));
        }
    }

    private void onCaptured(HotkeyService.CaptureResult r) {
        // 只在本元件確實還在錄製時才處理 (過期的 cancelled 事件直接忽略)
        if (active != this || !recording) return;
        try {
            stopRecording();
            if (r.cancelled()) return;
            if (r.errorKey() != null) {
                AppContext.get().ui().showError(I18n.t(r.errorKey()));
                return;
            }
            if (r.keys() != null && r.display() != null) {
                onChange.accept(r.toCombo());
            }
        } catch (Exception ex) {
            stopRecording();
            AppContext.get().ui().showError(
                    ex.getMessage() != null ? ex.getMessage() : I18n.t("recorder.error.unexpected"));
        }
    }

    private void stopRecording() {
        recording = false;
        if (active == this) active = null;
        Scene scene = getScene();
        if (scene != null) scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick);
        updateUi();
    }

    /** 點擊元件外部取消錄製 */
    private void onOutsideClick(MouseEvent e) {
        if (!recording) return;
        Object target = e.getTarget();
        if (target instanceof Node n && isInside(n)) return;
        try {
            AppContext.get().hotkeys().cancelCapture();
        } catch (Exception ignored) {
            // 略過
        }
        stopRecording();
    }

    private boolean isInside(Node n) {
        for (Node cur = n; cur != null; cur = cur.getParent()) {
            if (cur == this) return true;
        }
        return false;
    }
}
