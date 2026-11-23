package liu.poe.fiveway.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.KeyCombo;
import liu.poe.fiveway.model.Settings;
import liu.poe.fiveway.ui.component.KeyRecorder;
import liu.poe.fiveway.ui.component.ToggleSwitch;

/** 設定頁：透明度、置頂、語言、隱藏畫面快捷鍵、Attack Resume Delay、重設、檢查更新 */
public class SettingsPanelController {

    private static final double OP_MIN = 0.3;
    private static final double OP_MAX = 1.0;

    @FXML private Slider opacitySlider;
    @FXML private Label opacityLabel;
    @FXML private ToggleSwitch alwaysOnTopSwitch;
    @FXML private ComboBox<I18n.Language> languageCombo;
    @FXML private ToggleSwitch hideSwitch;
    @FXML private HBox hideRow;
    @FXML private KeyRecorder hideRecorder;
    @FXML private TextField resumeDelayField;
    @FXML private Button resetBtn;
    @FXML private Button checkBtn;

    private AppContext ctx;
    private boolean updating = false;
    private boolean checking = false;

    public void init() {
        ctx = AppContext.get();

        languageCombo.getItems().setAll(I18n.Language.values());
        languageCombo.setOnAction(e -> onLanguage());

        opacitySlider.valueProperty().addListener((o, ov, nv) -> {
            updateSliderTrack();
            if (updating) return;
            double v = nv.doubleValue();
            ctx.update(s -> s.opacity = v);
            ctx.window().setOpacity(v);
        });
        // 滑桿軌道要等 skin 建立後才能套用「已填滿」樣式
        opacitySlider.skinProperty().addListener((o, ov, nv) -> Platform.runLater(this::updateSliderTrack));

        hideRecorder.setOnChange(this::setHideHotkey);
        UiUtil.bindInt(resumeDelayField, () -> ctx.settings().attackResumeDelay,
                v -> ctx.update(s -> s.attackResumeDelay = v), false, 0);

        ctx.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        Settings s = ctx.settings();
        boolean running = ctx.isRunning();
        updating = true;
        try {
            opacitySlider.setMin(OP_MIN);
            opacitySlider.setMax(OP_MAX);
            opacitySlider.setValue(s.opacity);
            opacityLabel.setText(Math.round(s.opacity * 100) + "%");
            alwaysOnTopSwitch.setSelected(s.alwaysOnTop);
            languageCombo.setValue(I18n.current());
            hideSwitch.setSelected(s.hideEnabled);
            UiUtil.show(hideRow, s.hideEnabled);
            hideRecorder.setValue(s.hideHotkey);
            UiUtil.syncInt(resumeDelayField, s.attackResumeDelay, 0);
        } finally {
            updating = false;
        }
        updateSliderTrack();
        // 腳本啟動期間，除「透明度」「永遠置頂」「隱藏畫面」「語言」外，其餘控制停用
        resumeDelayField.setDisable(running);
        resetBtn.setDisable(running);
        checkBtn.setDisable(checking);
        checkBtn.setText(I18n.t(checking ? "btn.checking" : "btn.check"));
    }

    /** 軌道左側以強調色填滿到目前數值 */
    private void updateSliderTrack() {
        Node track = opacitySlider.lookup(".track");
        if (track == null) return;
        double pct = (opacitySlider.getValue() - OP_MIN) / (OP_MAX - OP_MIN) * 100;
        track.setStyle("-fx-background-color: linear-gradient(to right, -accent 0%, -accent "
                + pct + "%, -slider-track " + pct + "%, -slider-track 100%);");
    }

    private void setHideHotkey(KeyCombo v) {
        if (v != null) {
            String dup = ctx.duplicateOf(v, "hide");
            if (dup != null) {
                ctx.ui().showError(I18n.t("dup.message", dup));
                return;
            }
        }
        ctx.update(s -> s.hideHotkey = v);
    }

    @FXML
    private void onAlwaysOnTop() {
        boolean v = alwaysOnTopSwitch.isSelected();
        ctx.update(s -> s.alwaysOnTop = v);
        ctx.window().setAlwaysOnTop(v);
    }

    @FXML
    private void onHideSwitch() {
        boolean v = hideSwitch.isSelected();
        ctx.update(s -> s.hideEnabled = v);
    }

    private void onLanguage() {
        if (updating) return;
        I18n.Language lang = languageCombo.getValue();
        if (lang == null || lang == I18n.current()) return;
        ctx.update(s -> s.language = lang.code);
        I18n.setLanguage(lang);
        // 所有文字都要重新載入：重建整個 UI (設定都存在檔案/記憶體，不會遺失)
        Platform.runLater(() -> ctx.host().rebuildUi());
    }

    @FXML
    private void onReset() {
        ctx.ui().confirm(I18n.t("reset.title"), I18n.t("reset.message"), ctx::resetSettings);
    }

    @FXML
    private void onCheckUpdate() {
        if (checking) return;
        checking = true;
        refresh();
        ctx.updateFlow().check(true, () -> {
            checking = false;
            refresh();
        });
    }
}
