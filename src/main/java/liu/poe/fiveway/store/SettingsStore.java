package liu.poe.fiveway.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import liu.poe.fiveway.config.AppConfig;
import liu.poe.fiveway.config.AppPaths;
import liu.poe.fiveway.model.Settings;
import liu.poe.fiveway.model.WindowBounds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 設定持久化 (JSON)：
 * <pre>
 *   data/config/settings.json         目前設定
 *   data/backup/settings-&lt;ts&gt;.json    每次寫入前備份 (保留最新 10 份)
 * </pre>
 * 寫入採 150ms 防抖，避免 resize/move 密集事件造成大量 IO。
 */
public class SettingsStore {
    private static final Logger log = LoggerFactory.getLogger(SettingsStore.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final long DEBOUNCE_MS = 150;

    private final Path configFile = AppPaths.configFile();
    private final Path backupDir = AppPaths.backupDir();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "settings-writer");
        t.setDaemon(true);
        return t;
    });

    private Settings settings;
    private boolean writePending = false;
    private boolean pendingSkipBackup = true;

    public synchronized Settings get() {
        if (settings == null) load();
        return settings;
    }

    private void load() {
        ensureDirs();
        try {
            if (Files.exists(configFile)) {
                String raw = Files.readString(configFile, StandardCharsets.UTF_8);
                Settings s = GSON.fromJson(raw, Settings.class);
                settings = (s != null) ? s : new Settings();
                settings.normalize();
                log.info("已載入設定檔：{}", configFile);
            } else {
                settings = new Settings();
                log.info("找不到設定檔，建立預設設定：{}", configFile);
                save(true);
            }
        } catch (Exception e) {
            log.error("[store] 讀取設定失敗，改用預設值", e);
            settings = new Settings();
        }
    }

    /**
     * 排程寫入目前設定。
     *
     * @param skipBackup true 則不備份 (視窗位置/大小這類高頻變動)
     */
    public synchronized void save(boolean skipBackup) {
        ensureDirs();
        // 只要有一次變更需要備份，這一輪寫入就備份
        pendingSkipBackup = pendingSkipBackup && skipBackup;
        if (writePending) return;
        writePending = true;
        writer.schedule(this::flushNow, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    /** 立即寫入 (排程到期、或程式結束前呼叫) */
    public synchronized void flushNow() {
        if (settings == null) return;
        boolean skip = pendingSkipBackup;
        writePending = false;
        pendingSkipBackup = true;
        try {
            if (!skip) backupCurrent();
            Files.writeString(configFile, GSON.toJson(settings), StandardCharsets.UTF_8);
            log.debug("[store] 設定已寫入：{} (備份={})", configFile, !skip);
        } catch (Exception e) {
            log.error("[store] 寫入設定失敗", e);
        }
    }

    /** 重設為預設值 (保留視窗大小/位置與介面語言) */
    public synchronized Settings reset() {
        get();
        WindowBounds bounds = settings.windowBounds;
        String language = settings.language;
        settings = new Settings();
        if (bounds != null) settings.windowBounds = bounds;
        settings.language = language;
        log.info("[store] 設定已重設為預設值 (保留視窗位置/大小與語言)");
        save(false);
        return settings;
    }

    private void ensureDirs() {
        try {
            Files.createDirectories(configFile.getParent());
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            log.error("[store] 建立資料夾失敗", e);
        }
    }

    private void backupCurrent() {
        try {
            if (!Files.exists(configFile)) return;
            String ts = Instant.now().toString().replace(":", "-").replace(".", "-");
            Path dest = backupDir.resolve("settings-" + ts + ".json");
            Files.copy(configFile, dest, StandardCopyOption.REPLACE_EXISTING);
            log.debug("[store] 已備份設定檔：{}", dest.getFileName());
            // 清理舊備份，只保留最新 MAX_BACKUPS 份
            try (Stream<Path> files = Files.list(backupDir)) {
                List<Path> sorted = files
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.startsWith("settings-") && n.endsWith(".json");
                        })
                        .sorted(Comparator.comparingLong(this::mtime).reversed())
                        .toList();
                for (int i = AppConfig.MAX_BACKUPS; i < sorted.size(); i++) {
                    try {
                        Files.deleteIfExists(sorted.get(i));
                        log.debug("[store] 已清除舊備份：{}", sorted.get(i).getFileName());
                    } catch (IOException ignored) {
                        // 略過
                    }
                }
            }
        } catch (Exception e) {
            log.error("[store] 設定檔備份失敗", e);
        }
    }

    private long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
