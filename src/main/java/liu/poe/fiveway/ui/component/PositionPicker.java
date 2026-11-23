package liu.poe.fiveway.ui.component;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.input.MouseSender;
import liu.poe.fiveway.model.ScreenPoint;
import liu.poe.fiveway.ui.UiUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * 螢幕座標擷取元件 (對應原版 PositionPicker；雙人刷新模式 / 自動控制鼠標位置 共用)。
 * 流程：按下「擷取」→ 倒數 n 秒 → 記錄目前滑鼠的絕對螢幕座標。
 * 擷取後可直接在 X / Y 欄位手動微調 (輸入數字即生效)。
 * 使用絕對座標的原因：雙螢幕各一個視窗、或單螢幕兩個視窗都能正確對應。
 */
public class PositionPicker extends HBox {

    private static final int COUNTDOWN_SEC = 3;

    @FXML private HBox posEdit;
    @FXML private TextField xField;
    @FXML private TextField yField;
    @FXML private Label posValue;
    @FXML private Button captureBtn;
    @FXML private Button clearBtn;

    private ScreenPoint value;
    private int left = 0;
    private Timeline countdown;
    private Consumer<ScreenPoint> onChange = v -> { };

    public PositionPicker() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/position_picker.fxml"), I18n.bundle());
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        xField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("-?\\d{0,9}") ? c : null));
        yField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("-?\\d{0,9}") ? c : null));
        // 手動微調：直接編輯 X / Y (空字串視為 0，非數字忽略)
        xField.textProperty().addListener((o, ov, nv) -> edit(true, nv));
        yField.textProperty().addListener((o, ov, nv) -> edit(false, nv));

        disabledProperty().addListener((o, ov, nv) -> updateUi());
        // 離開場景時停止倒數
        sceneProperty().addListener((o, a, b) -> {
            if (b == null) cancelCountdown();
        });
        updateUi();
    }

    public void setValue(ScreenPoint v) {
        this.value = v;
        updateUi();
    }

    public void setOnChange(Consumer<ScreenPoint> onChange) {
        this.onChange = onChange != null ? onChange : v -> { };
    }

    private boolean capturing() {
        return left > 0;
    }

    private void updateUi() {
        boolean has = value != null;
        boolean busy = isDisabled() || capturing();

        UiUtil.show(posEdit, has);
        UiUtil.show(posValue, !has);
        if (has) {
            syncField(xField, value.x);
            syncField(yField, value.y);
        }
        posEdit.setDisable(busy);

        captureBtn.setDisable(busy);
        captureBtn.setText(capturing()
                ? I18n.t("pos.countdown", left)
                : I18n.t(has ? "pos.recapture" : "pos.capture"));

        UiUtil.show(clearBtn, has);
        clearBtn.setDisable(busy);
    }

    private void syncField(TextField tf, int v) {
        String s = String.valueOf(v);
        if (s.equals(tf.getText())) return;
        // 使用者正在輸入且數值相同時，不覆蓋其輸入中的文字
        if (tf.isFocused() && UiUtil.parseInt(tf.getText(), 0) == v) return;
        tf.setText(s);
    }

    private void edit(boolean isX, String raw) {
        if (value == null) return;
        int n = UiUtil.parseInt(raw, 0);
        ScreenPoint p = new ScreenPoint(value.x, value.y);
        if (isX) p.x = n;
        else p.y = n;
        if (p.x == value.x && p.y == value.y) return;
        onChange.accept(p);
    }

    @FXML
    private void onCapture() {
        if (isDisabled() || capturing()) return;
        left = COUNTDOWN_SEC;
        updateUi();
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            left--;
            if (left <= 0) {
                left = 0;
                if (countdown != null) countdown.stop();
                ScreenPoint p = MouseSender.currentPosition();
                if (p != null) onChange.accept(p);   // 擷取失敗時維持原值
            }
            updateUi();
        }));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    @FXML
    private void onClear() {
        onChange.accept(null);
    }

    private void cancelCountdown() {
        if (countdown != null) countdown.stop();
        left = 0;
    }
}
