package liu.poe.fiveway.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import liu.poe.fiveway.i18n.I18n;

/** 通用對話框 (訊息 / 確認 / 進度) */
public class DialogController {

    @FXML private StackPane backdrop;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private ProgressBar progressBar;
    @FXML private HBox actions;
    @FXML private Button cancelBtn;
    @FXML private Button confirmBtn;

    private Runnable onConfirm;
    private boolean progressMode;

    public void show(String title, String message, String confirmText, String cancelText, Runnable onConfirm) {
        this.onConfirm = onConfirm;
        this.progressMode = false;
        titleLabel.setText(title);
        messageLabel.setText(message);
        UiUtil.show(progressBar, false);
        UiUtil.show(actions, true);
        confirmBtn.setText(confirmText != null ? confirmText : I18n.t("common.ok"));
        UiUtil.show(cancelBtn, cancelText != null);
        if (cancelText != null) cancelBtn.setText(cancelText);
        UiUtil.show(backdrop, true);
    }

    public void showProgress(String title, String message) {
        this.onConfirm = null;
        this.progressMode = true;
        titleLabel.setText(title);
        messageLabel.setText(message);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        UiUtil.show(progressBar, true);
        UiUtil.show(actions, false);
        UiUtil.show(backdrop, true);
    }

    public void setProgress(double value) {
        progressBar.setProgress(value < 0 ? ProgressIndicator.INDETERMINATE_PROGRESS : value);
    }

    public void close() {
        UiUtil.show(backdrop, false);
        progressMode = false;
    }

    @FXML
    private void onBackdropClick() {
        if (!progressMode) close();
    }

    @FXML
    private void consume(MouseEvent e) {
        e.consume();
    }

    @FXML
    private void onCancel() {
        close();
    }

    /** 先關閉再執行回呼，讓回呼可以再開啟新的對話框 */
    @FXML
    private void onConfirm() {
        Runnable cb = onConfirm;
        close();
        if (cb != null) cb.run();
    }
}
