package liu.poe.fiveway.app;

/** 應用程式層級的操作 (由 PoeFiveWayApp 實作) */
public interface AppHost {

    /** 重新載入整個 UI (切換語言後使用) */
    void rebuildUi();

    /** 完整結束程式：停止腳本、卸載鉤子、儲存設定 */
    void exitApp();

    /** 以系統預設瀏覽器開啟網址 */
    void openExternal(String url);
}
