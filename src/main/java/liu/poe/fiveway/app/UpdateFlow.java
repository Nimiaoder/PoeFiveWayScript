package liu.poe.fiveway.app;

import javafx.application.Platform;
import liu.poe.fiveway.config.AppConfig;
import liu.poe.fiveway.config.AppPaths;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.ui.UiHost;
import liu.poe.fiveway.update.UpdateInstaller;
import liu.poe.fiveway.update.UpdateService;

import java.nio.file.Path;

/**
 * 自動更新流程：檢查 → 詢問 → 下載 (顯示進度) → 套用 → 結束程式 (由更新腳本/安裝程式接手)。
 * 開發模式或找不到對應附件時，退回「開啟 Release 頁面」(與原版行為相同)。
 */
public final class UpdateFlow {

    private final AppContext ctx;
    private boolean busy = false;

    public UpdateFlow(AppContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 檢查更新。
     *
     * @param manual true=使用者手動檢查 (一定會顯示結果)；false=啟動時自動檢查 (有新版才提示)
     * @param onDone 檢查結束後回呼 (JavaFX 執行緒)，可為 null
     */
    public void check(boolean manual, Runnable onDone) {
        ctx.background().execute(() -> {
            UpdateService.CheckResult r = ctx.updates().check();
            Platform.runLater(() -> {
                try {
                    handle(r, manual);
                } finally {
                    if (onDone != null) onDone.run();
                }
            });
        });
    }

    private void handle(UpdateService.CheckResult r, boolean manual) {
        UiHost ui = ctx.ui();
        if (!r.ok()) {
            if (manual) ui.showMessage(I18n.t("update.failed.title"), errorText(r));
            return;
        }
        if (!r.hasUpdate()) {
            if (manual) {
                ui.showMessage(I18n.t("update.latest.title"), I18n.t("update.latest.message", r.current()));
            }
            return;
        }

        String message = I18n.t("update.available.message", r.current(), r.latest());
        boolean canAutoUpdate = r.asset() != null && (AppConfig.isPortable() || AppConfig.isInstaller())
                && !r.asset().url().isEmpty();
        if (canAutoUpdate) {
            ui.showDialog(I18n.t("update.available.title"), message,
                    I18n.t("update.btn.now"), I18n.t("common.close"),
                    () -> download(r.asset()));
        } else {
            String url = r.pageUrl();
            ui.showDialog(I18n.t("update.available.title"), message,
                    I18n.t("update.btn.download"), I18n.t("common.close"),
                    () -> {
                        if (url != null && !url.isEmpty()) ctx.host().openExternal(url);
                    });
        }
    }

    private String errorText(UpdateService.CheckResult r) {
        String base = r.errorKey() != null ? I18n.t(r.errorKey()) : null;
        String detail = r.errorDetail();
        if (base != null && detail != null && !detail.isEmpty()) return base + "\n" + detail;
        if (base != null) return base;
        return (detail != null && !detail.isEmpty()) ? detail : I18n.t("dialog.unknownError");
    }

    private void download(UpdateService.Asset asset) {
        if (busy) return;
        busy = true;
        UiHost ui = ctx.ui();
        // 更新前先停止腳本，避免按鍵殘留
        ctx.stopScript();
        ui.showProgress(I18n.t("update.downloading.title"), I18n.t("update.downloading.message", asset.name()));

        ctx.background().execute(() -> {
            try {
                Path target = AppPaths.tempDir().resolve(asset.name());
                ctx.updates().download(asset, target, p -> Platform.runLater(() -> ui.setProgress(p)));
                Platform.runLater(() -> apply(target));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    busy = false;
                    ui.closeProgress();
                    ui.showMessage(I18n.t("update.failed.title"),
                            I18n.t("update.error.download", String.valueOf(e.getMessage())));
                });
            }
        });
    }

    private void apply(Path downloaded) {
        UiHost ui = ctx.ui();
        try {
            UpdateInstaller.apply(downloaded);
            // 更新腳本 / 安裝程式已啟動，立刻結束本程式讓它接手
            ctx.host().exitApp();
        } catch (Exception e) {
            busy = false;
            ui.closeProgress();
            ui.showMessage(I18n.t("update.failed.title"),
                    I18n.t("update.error.apply", String.valueOf(e.getMessage())));
        }
    }
}
