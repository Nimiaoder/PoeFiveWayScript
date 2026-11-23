package liu.poe.fiveway.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.SVGPath;
import liu.poe.fiveway.app.AppContext;
import liu.poe.fiveway.config.AppConfig;

/** 自訂標題列：名稱與版本 + 最小化 / 最大化 / 關閉 + 拖曳 */
public class TitleBarController {

    @FXML private Label titleLabel;
    @FXML private Label versionLabel;
    @FXML private SVGPath maxIcon;
    @FXML private SVGPath restoreIcon;

    private WindowManager window;

    public void init() {
        AppContext ctx = AppContext.get();
        window = ctx.window();
        titleLabel.setText(AppConfig.appName());
        versionLabel.setText("v" + AppConfig.appVersion());
        window.maximizedProperty().addListener((o, ov, nv) -> updateMaxIcon());
        updateMaxIcon();
    }

    private void updateMaxIcon() {
        boolean max = window.isMaximized();
        UiUtil.show(maxIcon, !max);
        UiUtil.show(restoreIcon, max);
    }

    @FXML
    private void onDragPressed(MouseEvent e) {
        window.onDragPressed(e);
    }

    @FXML
    private void onDragged(MouseEvent e) {
        window.onDragged(e);
    }

    /** 雙擊標題列切換最大化 */
    @FXML
    private void onDragClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) window.toggleMaximize();
    }

    @FXML
    private void onMinimize() {
        window.minimize();
    }

    @FXML
    private void onToggleMaximize() {
        window.toggleMaximize();
    }

    @FXML
    private void onClose() {
        AppContext.get().host().exitApp();
    }
}
