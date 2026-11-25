package liu.poe.fiveway.ui;

/** UI 層對外提供的對話框 / 編輯器功能 (由 MainController 實作) */
public interface UiHost {

    /** 以「無法設定」為標題顯示錯誤訊息 */
    void showError(String message);

    /** 顯示只有「確定」按鈕的訊息 */
    void showMessage(String title, String message);

    /** 通用對話框；cancelText 為 null 則不顯示取消鈕 */
    void showDialog(String title, String message, String confirmText, String cancelText, Runnable onConfirm);

    /** 只有一個按鈕的強制提示：不能以點擊背景關閉，按下按鈕後一定會執行 onConfirm */
    void showNotice(String title, String message, String confirmText, Runnable onConfirm);

    /** 確定 / 取消 的確認對話框 */
    void confirm(String title, String message, Runnable onOk);

    /** 顯示進度對話框 (無按鈕，用於下載更新) */
    void showProgress(String title, String message);

    /** 0~1；小於 0 代表不確定進度 */
    void setProgress(double value);

    void closeProgress();

    /** 開啟 Buff 新增 (buffId 為 null) / 編輯視窗 */
    void openBuffEditor(String buffId);
}
