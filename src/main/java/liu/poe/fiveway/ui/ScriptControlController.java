package liu.poe.fiveway.ui;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.HBox;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.KeyCombo;
import liu.poe.fiveway.model.Settings;
import liu.poe.fiveway.ui.component.KeyRecorder;
import liu.poe.fiveway.ui.component.ToggleSwitch;

/** 區塊一：腳本控制 (啟動/關閉、快捷鍵、快捷鍵模式) + 模式 (自動攻擊/刷新/Buff) */
public class ScriptControlController {

    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");

    @FXML private HBox statusPill;
    @FXML private Label statusLabel;
    @FXML private ToggleSwitch runSwitch;
    @FXML private KeyRecorder hotkeyRecorder;
    @FXML private RadioButton listenRadio;
    @FXML private RadioButton interceptRadio;
    @FXML private CheckBox attackerCheck;
    @FXML private CheckBox refreshCheck;
    @FXML private CheckBox buffCheck;

    private AppContext ctx;

    public void init() {
        ctx = AppContext.get();
        hotkeyRecorder.setOnChange(this::setToggle);
        ctx.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        Settings s = ctx.settings();
        boolean running = ctx.isRunning();

        statusPill.pseudoClassStateChanged(ON, running);
        statusLabel.setText(I18n.t(running ? "status.running" : "status.stopped"));
        runSwitch.setSelected(running);

        hotkeyRecorder.setValue(s.toggleHotkey);
        hotkeyRecorder.setDisable(running);

        listenRadio.setSelected(Settings.HOTKEY_LISTEN.equals(s.hotkeyMode));
        interceptRadio.setSelected(Settings.HOTKEY_INTERCEPT.equals(s.hotkeyMode));
        listenRadio.setDisable(running);
        interceptRadio.setDisable(running);

        attackerCheck.setSelected(s.attackerMode);
        refreshCheck.setSelected(s.refreshMode);
        buffCheck.setSelected(s.buffMode);
        attackerCheck.setDisable(running);
        refreshCheck.setDisable(running);
        buffCheck.setDisable(running);
    }

    /** 快捷鍵設定：檢查重複 */
    private void setToggle(KeyCombo v) {
        if (v != null) {
            String dup = ctx.duplicateOf(v, "toggle");
            if (dup != null) {
                ctx.ui().showError(I18n.t("dup.message", dup));
                return;
            }
        }
        ctx.update(s -> s.toggleHotkey = v);
    }

    @FXML
    private void onRunSwitch() {
        boolean want = runSwitch.isSelected();
        // 開關狀態由「是否真的在執行」決定：先還原，成功啟動後 refresh 會再打開
        runSwitch.setSelected(ctx.isRunning());
        if (want) ctx.startScript();
        else ctx.stopScript();
    }

    @FXML
    private void onListen() {
        ctx.update(s -> s.hotkeyMode = Settings.HOTKEY_LISTEN);
    }

    @FXML
    private void onIntercept() {
        ctx.update(s -> s.hotkeyMode = Settings.HOTKEY_INTERCEPT);
    }

    @FXML
    private void onAttackerMode() {
        boolean v = attackerCheck.isSelected();
        ctx.update(s -> s.attackerMode = v);
    }

    @FXML
    private void onRefreshMode() {
        boolean v = refreshCheck.isSelected();
        ctx.update(s -> s.refreshMode = v);
    }

    @FXML
    private void onBuffMode() {
        boolean v = buffCheck.isSelected();
        ctx.update(s -> s.buffMode = v);
    }
}
