package liu.poe.fiveway.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import liu.poe.fiveway.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.DoubleConsumer;
import java.util.regex.Pattern;

/**
 * GitHub Release 更新檢查與下載。
 * 更新來源在 app.properties 的 update.repo 設定 (格式：owner/repo)。
 */
public final class UpdateService {
    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    /** Release 附件 */
    public record Asset(String name, String url, long size) {
    }

    /**
     * 檢查結果。
     * ok=false 時 errorKey 為 i18n key (可為 null)，errorDetail 為原始訊息。
     * asset 為符合目前發行型態 (portable / installer) 的附件；找不到或開發模式為 null。
     */
    public record CheckResult(boolean ok, boolean hasUpdate, String latest, String current,
                              String pageUrl, Asset asset, String errorKey, String errorDetail) {
        static CheckResult error(String key, String detail) {
            return new CheckResult(false, false, null, null, null, null, key, detail);
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(AppConfig.updateTimeoutSec()))
            .build();

    /** 檢查更新 (阻塞，請在背景執行緒呼叫) */
    public CheckResult check() {
        String current = AppConfig.appVersion();
        try {
            String url = AppConfig.updateApiBase() + "/repos/" + AppConfig.updateRepo() + "/releases/latest";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(AppConfig.updateTimeoutSec()))
                    .header("User-Agent", "poe-five-army-script")
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            JsonObject data;
            try {
                data = JsonParser.parseString(res.body()).getAsJsonObject();
            } catch (Exception e) {
                return CheckResult.error("update.error.noVersion", "HTTP " + res.statusCode());
            }
            String tag = str(data, "tag_name");
            String latest = tag.replaceFirst("^v", "");
            if (latest.isEmpty()) {
                return CheckResult.error("update.error.noVersion", "HTTP " + res.statusCode());
            }

            Version vLatest = Version.parse(latest);
            Version vCurrent = Version.parse(current);
            boolean hasUpdate = vLatest != null && vCurrent != null && vLatest.compareTo(vCurrent) > 0;

            return new CheckResult(true, hasUpdate, latest, current, str(data, "html_url"),
                    pickAsset(data), null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckResult.error(null, e.getMessage());
        } catch (Exception e) {
            log.warn("檢查更新失敗：{}", e.toString());
            return CheckResult.error(null, e.getMessage());
        }
    }

    /** 依發行型態挑選對應的 Release 附件 */
    private Asset pickAsset(JsonObject release) {
        String regex;
        if (AppConfig.isPortable()) regex = AppConfig.portableAssetRegex();
        else if (AppConfig.isInstaller()) regex = AppConfig.installerAssetRegex();
        else return null;

        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        JsonElement assetsEl = release.get("assets");
        if (assetsEl == null || !assetsEl.isJsonArray()) return null;
        JsonArray assets = assetsEl.getAsJsonArray();
        for (JsonElement el : assets) {
            JsonObject a = el.getAsJsonObject();
            String name = str(a, "name");
            if (p.matcher(name).matches()) {
                long size = a.has("size") && !a.get("size").isJsonNull() ? a.get("size").getAsLong() : -1;
                return new Asset(name, str(a, "browser_download_url"), size);
            }
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? "" : e.getAsString();
    }

    /** 下載附件到 target (阻塞，請在背景執行緒呼叫)；progress 為 0~1，未知大小時為 -1 */
    public void download(Asset asset, Path target, DoubleConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");

        HttpRequest req = HttpRequest.newBuilder(URI.create(asset.url()))
                .header("User-Agent", "poe-five-army-script")
                .GET()
                .build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            throw new IOException("HTTP " + res.statusCode());
        }
        long total = res.headers().firstValueAsLong("Content-Length").orElse(asset.size());

        try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                out.write(buf, 0, n);
                done += n;
                progress.accept(total > 0 ? Math.min(1.0, (double) done / total) : -1);
            }
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        progress.accept(1.0);
    }
}
