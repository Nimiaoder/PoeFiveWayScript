package liu.poe.fiveway.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 資料夾位置：
 * <ul>
 *   <li>開發 (dev)：專案工作目錄 ./data</li>
 *   <li>免安裝 (portable)：與 exe 同層 ./data (資料跟著程式走，與原版行為相同)</li>
 *   <li>安裝版 (installer)：%APPDATA%\PoeFiveWayScript\data (安裝目錄通常無寫入權限)</li>
 * </ul>
 */
public final class AppPaths {

    private AppPaths() {
    }

    /** jpackage 啟動器 (exe) 的完整路徑；開發模式為 null */
    public static Path launcherExe() {
        String p = System.getProperty("jpackage.app-path");
        return (p == null || p.isBlank()) ? null : Paths.get(p);
    }

    /** 程式安裝/解壓目錄 (exe 所在資料夾)；開發模式為 null */
    public static Path appDir() {
        Path exe = launcherExe();
        return exe == null ? null : exe.getParent();
    }

    public static Path dataRoot() {
        Path root;
        if (AppConfig.isInstaller()) {
            String appData = System.getenv("APPDATA");
            Path base = (appData == null || appData.isBlank())
                    ? Paths.get(System.getProperty("user.home"))
                    : Paths.get(appData);
            root = base.resolve(AppConfig.appName());
        } else if (appDir() != null) {
            root = appDir();
        } else {
            root = Paths.get(System.getProperty("user.dir"));
        }
        return root.resolve("data");
    }

    public static Path configDir() {
        return dataRoot().resolve("config");
    }

    public static Path backupDir() {
        return dataRoot().resolve("backup");
    }

    public static Path configFile() {
        return configDir().resolve("settings.json");
    }

    public static Path tempDir() {
        return Paths.get(System.getProperty("java.io.tmpdir"), AppConfig.appName() + "-update");
    }
}
