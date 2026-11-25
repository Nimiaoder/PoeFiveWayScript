package liu.poe.fiveway.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import liu.poe.fiveway.app.AppContext;

/** 主畫面：標題列 + 分頁 (主頁 / 設定) + 對話框覆蓋層 */
public class MainController implements UiHost {

    @FXML private ToggleGroup tabGroup;
    @FXML private ToggleButton homeTab;
    @FXML private ToggleButton settingsTab;
    @FXML private ScrollPane homeScroll;
    @FXML private ScrollPane settingsScroll;

    // fx:include 的子控制器 (名稱 = fx:id + "Controller")
    @FXML private TitleBarController titleBarController;
    @FXML private ScriptControlController scriptControlController;
    @FXML private KeyConfigController keyConfigController;
    @FXML private BuffPanelController buffPanelController;
    @FXML private SettingsPanelController settingsPanelController;
    @FXML private BuffEditorController buffEditorController;
    @FXML private DialogController dialogController;

    @FXML
    private void initialize() {
        // 分頁不可被取消選取
        tabGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            } else {
                showTab();
            }
        });
    }

    /** 由 PoeFiveWayApp 在 FXML 載入完成後呼叫 */
    public void init(int tabIndex) {
        AppContext.get().setUi(this);
        titleBarController.init();
        scriptControlController.init();
        keyConfigController.init();
        buffPanelController.init();
        settingsPanelController.init();
        (tabIndex == 1 ? settingsTab : homeTab).setSelected(true);
        showTab();
    }

    public int selectedTab() {
        Toggle t = tabGroup.getSelectedToggle();
        return t == settingsTab ? 1 : 0;
    }

    private void showTab() {
        boolean home = selectedTab() == 0;
        UiUtil.show(homeScroll, home);
        UiUtil.show(settingsScroll, !home);
    }

    // ── UiHost ─────────────────────────────────────────────────────────
    @Override
    public void showError(String message) {
        dialogController.show(liu.poe.fiveway.i18n.I18n.t("dialog.title.cannotSet"), message, null, null, null);
    }

    @Override
    public void showMessage(String title, String message) {
        dialogController.show(title, message, null, null, null);
    }

    @Override
    public void showDialog(String title, String message, String confirmText, String cancelText, Runnable onConfirm) {
        dialogController.show(title, message, confirmText, cancelText, onConfirm);
    }

    @Override
    public void showNotice(String title, String message, String confirmText, Runnable onConfirm) {
        dialogController.showNotice(title, message, confirmText, onConfirm);
    }

    @Override
    public void confirm(String title, String message, Runnable onOk) {
        dialogController.show(title, message, null, liu.poe.fiveway.i18n.I18n.t("common.cancel"), onOk);
    }

    @Override
    public void showProgress(String title, String message) {
        dialogController.showProgress(title, message);
    }

    @Override
    public void setProgress(double value) {
        dialogController.setProgress(value);
    }

    @Override
    public void closeProgress() {
        dialogController.close();
    }

    @Override
    public void openBuffEditor(String buffId) {
        buffEditorController.open(buffId);
    }
}
