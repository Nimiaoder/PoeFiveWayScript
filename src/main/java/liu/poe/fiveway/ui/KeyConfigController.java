package liu.poe.fiveway.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.KeyCombo;
import liu.poe.fiveway.model.Settings;
import liu.poe.fiveway.ui.component.KeyRecorder;
import liu.poe.fiveway.ui.component.PositionPicker;
import liu.poe.fiveway.ui.component.Section;
import liu.poe.fiveway.ui.component.ToggleSwitch;

import java.util.function.BiConsumer;

/**
 * 區塊二：按鍵設定 (Attack + Refresh)
 * - 未勾選「自動攻擊」時隱藏 Attack 區
 * - 未勾選「自動刷新」時隱藏 Refresh 相關區塊
 * - 同時勾選「自動攻擊 + 自動刷新」時，才會出現「時空裂隙」與「雙人刷新模式」
 */
public class KeyConfigController {

    @FXML private VBox root;
    @FXML private ToggleGroup dirGroup;

    @FXML private Section attackSection;
    @FXML private KeyRecorder attackKey;

    @FXML private Section refreshSection;
    @FXML private KeyRecorder enterKey;
    @FXML private TextField enterWait;
    @FXML private KeyRecorder exitKey;
    @FXML private TextField exitWait;
    @FXML private RadioButton dirEnter;
    @FXML private RadioButton dirExit;
    @FXML private Button dirClear;

    @FXML private Section riftSection;
    @FXML private ToggleSwitch riftSwitch;
    @FXML private HBox riftKeyRow;
    @FXML private KeyRecorder riftKey;

    @FXML private Section dualSection;
    @FXML private ToggleSwitch dualSwitch;
    @FXML private VBox dualDetail;
    @FXML private PositionPicker attackerPos;
    @FXML private PositionPicker auraPos;
    @FXML private ToggleSwitch rightClickSwitch;
    @FXML private TextField focusDelay;

    @FXML private Section mouseSection;
    @FXML private ToggleSwitch mouseSwitch;
    @FXML private VBox mouseDetail;
    @FXML private PositionPicker mousePos;

    private AppContext ctx;

    public void init() {
        ctx = AppContext.get();

        attackKey.setOnChange(v -> setKey("attackKey", v, (s, k) -> s.attackKey = k));
        enterKey.setOnChange(v -> setKey("enterKey", v, (s, k) -> s.enterKey = k));
        exitKey.setOnChange(v -> setKey("exitKey", v, (s, k) -> s.exitKey = k));
        riftKey.setOnChange(v -> setKey("riftKey", v, (s, k) -> s.riftKey = k));

        UiUtil.bindInt(enterWait, () -> ctx.settings().enterWaitMs, v -> ctx.update(s -> s.enterWaitMs = v), false, 0);
        UiUtil.bindInt(exitWait, () -> ctx.settings().exitWaitMs, v -> ctx.update(s -> s.exitWaitMs = v), false, 0);
        UiUtil.bindInt(focusDelay, () -> ctx.settings().dualFocusDelayMs, v -> ctx.update(s -> s.dualFocusDelayMs = v), false, 0);

        attackerPos.setOnChange(p -> ctx.update(s -> s.dualAttackerPos = p));
        auraPos.setOnChange(p -> ctx.update(s -> s.dualAuraPos = p));
        mousePos.setOnChange(p -> ctx.update(s -> s.mousePos = p));

        ctx.addListener(this::refresh);
        refresh();
    }

    /** 統一：設定按鍵前檢查重複 */
    private void setKey(String field, KeyCombo v, BiConsumer<Settings, KeyCombo> setter) {
        if (v != null) {
            String dup = ctx.duplicateOf(v, field);
            if (dup != null) {
                ctx.ui().showError(I18n.t("dup.message", dup));
                return;
            }
        }
        ctx.update(s -> setter.accept(s, v));
    }

    private void refresh() {
        Settings s = ctx.settings();
        boolean disabled = ctx.isRunning();
        // 是否可使用雙人刷新模式：打手(自動攻擊) + 刷新 同時開啟
        boolean dualAvailable = s.attackerMode && s.refreshMode;
        boolean dualOn = dualAvailable && s.dualRefreshMode;

        boolean showAttack = s.attackerMode;
        boolean showRefresh = s.refreshMode;
        boolean showMouse = s.refreshMode && !dualOn;
        UiUtil.show(attackSection, showAttack);
        UiUtil.show(refreshSection, showRefresh);
        UiUtil.show(riftSection, dualAvailable);
        UiUtil.show(dualSection, dualAvailable);
        UiUtil.show(mouseSection, showMouse);
        UiUtil.show(root, showAttack || showRefresh);   // 全部隱藏時不佔版面 (避免多出一段間距)

        // Attack
        attackKey.setValue(s.attackKey);
        attackKey.setDisable(disabled);

        // Refresh
        enterKey.setValue(s.enterKey);
        enterKey.setDisable(disabled);
        exitKey.setValue(s.exitKey);
        exitKey.setDisable(disabled);
        UiUtil.syncInt(enterWait, s.enterWaitMs, 0);
        UiUtil.syncInt(exitWait, s.exitWaitMs, 0);
        enterWait.setDisable(disabled);
        exitWait.setDisable(disabled);
        dirEnter.setSelected(Settings.DIR_ENTER_FIRST.equals(s.refreshDirection));
        dirExit.setSelected(Settings.DIR_EXIT_FIRST.equals(s.refreshDirection));
        if (s.refreshDirection == null) dirGroup.selectToggle(null);
        dirEnter.setDisable(disabled);
        dirExit.setDisable(disabled);
        dirClear.setDisable(disabled);

        // 時空裂隙
        riftSwitch.setSelected(s.riftEnabled);
        riftSwitch.setDisable(disabled);
        UiUtil.show(riftKeyRow, s.riftEnabled);
        riftKey.setValue(s.riftKey);
        riftKey.setDisable(disabled);

        // 雙人刷新
        dualSwitch.setSelected(s.dualRefreshMode);
        dualSwitch.setDisable(disabled);
        UiUtil.show(dualDetail, dualOn);
        attackerPos.setValue(s.dualAttackerPos);
        attackerPos.setDisable(disabled);
        auraPos.setValue(s.dualAuraPos);
        auraPos.setDisable(disabled);
        rightClickSwitch.setSelected(s.dualRightClickFocus);
        rightClickSwitch.setDisable(disabled);
        UiUtil.syncInt(focusDelay, s.dualFocusDelayMs, 0);
        focusDelay.setDisable(disabled);

        // 自動控制鼠標位置 (單人刷新用；雙人模式下不適用)
        mouseSwitch.setSelected(s.mouseControl);
        mouseSwitch.setDisable(disabled);
        UiUtil.show(mouseDetail, s.mouseControl);
        mousePos.setValue(s.mousePos);
        mousePos.setDisable(disabled);
    }

    // ── 事件 ───────────────────────────────────────────────────────────
    @FXML
    private void onDirEnter() {
        ctx.update(s -> s.refreshDirection = Settings.DIR_ENTER_FIRST);
    }

    @FXML
    private void onDirExit() {
        ctx.update(s -> s.refreshDirection = Settings.DIR_EXIT_FIRST);
    }

    @FXML
    private void onDirClear() {
        ctx.update(s -> s.refreshDirection = null);
    }

    @FXML
    private void onRiftSwitch() {
        boolean v = riftSwitch.isSelected();
        ctx.update(s -> s.riftEnabled = v);
    }

    @FXML
    private void onDualSwitch() {
        boolean v = dualSwitch.isSelected();
        ctx.update(s -> s.dualRefreshMode = v);
    }

    @FXML
    private void onRightClickSwitch() {
        boolean v = rightClickSwitch.isSelected();
        ctx.update(s -> s.dualRightClickFocus = v);
    }

    @FXML
    private void onMouseSwitch() {
        boolean v = mouseSwitch.isSelected();
        ctx.update(s -> s.mouseControl = v);
    }
}
