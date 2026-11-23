package liu.poe.fiveway.engine;

/** 腳本啟動結果；失敗時 errorKey 為 i18n key (可為 null)，errorDetail 為原始例外訊息 */
public record StartResult(boolean ok, String errorKey, String errorDetail) {
    public static StartResult success() {
        return new StartResult(true, null, null);
    }

    public static StartResult failure(String errorKey, String errorDetail) {
        return new StartResult(false, errorKey, errorDetail);
    }
}
