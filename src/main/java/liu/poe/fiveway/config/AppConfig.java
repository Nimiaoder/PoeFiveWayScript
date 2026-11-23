package liu.poe.fiveway.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 應用程式設定：讀取 resources/app.properties，並集中放置固定常數。
 * 更新來源 (update.repo) 請至 app.properties 修改。
 */
public final class AppConfig {

    /** 所有模擬按鍵固定按壓毫秒數 */
    public static final int HOLD_MS = 40;
    /** 視窗最小尺寸 / 預設尺寸 */
    public static final int MIN_WIDTH = 480;
    public static final int MIN_HEIGHT = 640;
    public static final int DEFAULT_WIDTH = 520;
    public static final int DEFAULT_HEIGHT = 780;
    /** 設定檔備份保留份數 */
    public static final int MAX_BACKUPS = 10;
    /** 找不到設定時的更新來源預設值 */
    public static final String SAMPLE_REPO = "Sample/PoeFiveWayScript";

    private static final Properties PROPS = load();

    private AppConfig() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // 使用預設值
        }
        return p;
    }

    /** 取值；若值為空或尚未被 Maven 取代 (仍是 ${...}) 則回傳預設值 */
    private static String get(String key, String def) {
        String v = PROPS.getProperty(key);
        if (v == null || v.isBlank() || v.contains("${")) return def;
        return v.trim();
    }

    public static String appName() {
        return get("app.name", "PoeFiveWayScript");
    }

    public static String appVersion() {
        return get("app.version", "0.0.0");
    }

    /** 發行型態：dev / portable / installer (由 jpackage 的 -Dapp.distribution 決定) */
    public static String distribution() {
        return System.getProperty("app.distribution", "dev");
    }

    public static boolean isPortable() {
        return "portable".equalsIgnoreCase(distribution());
    }

    public static boolean isInstaller() {
        return "installer".equalsIgnoreCase(distribution());
    }

    public static String updateRepo() {
        return get("update.repo", SAMPLE_REPO);
    }

    public static String updateApiBase() {
        return get("update.api.base", "https://api.github.com");
    }

    public static boolean checkUpdateOnStartup() {
        return Boolean.parseBoolean(get("update.checkOnStartup", "true"));
    }

    public static int updateTimeoutSec() {
        try {
            return Integer.parseInt(get("update.timeoutSec", "8"));
        } catch (NumberFormatException e) {
            return 8;
        }
    }

    public static String portableAssetRegex() {
        return get("update.asset.portable.regex", ".*portable.*\\.zip");
    }

    public static String installerAssetRegex() {
        return get("update.asset.installer.regex", ".*\\.(exe|msi)");
    }

    public static int singleInstancePort() {
        try {
            return Integer.parseInt(get("single.instance.port", "47213"));
        } catch (NumberFormatException e) {
            return 47213;
        }
    }
}
