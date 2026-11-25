package liu.poe.fiveway.update;

import liu.poe.fiveway.config.AppConfig;
import liu.poe.fiveway.config.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 套用已下載的更新。呼叫成功後，呼叫端必須立刻結束程式。
 * <ul>
 *   <li>免安裝版 (portable)：解壓 zip → 產生批次檔 → 以隱藏視窗執行，等本程式結束後覆蓋程式檔 (保留 data 資料夾) → 重新啟動</li>
 *   <li>安裝版 (installer)：直接執行下載的安裝程式 (jpackage 的 upgrade UUID 固定，會覆蓋升級)</li>
 * </ul>
 */
public final class UpdateInstaller {

    private UpdateInstaller() {
    }

    public static void apply(Path downloaded) throws IOException {
        if (AppConfig.isPortable()) {
            applyPortable(downloaded);
        } else if (AppConfig.isInstaller()) {
            applyInstaller(downloaded);
        } else {
            throw new IOException("Not a packaged build");
        }
    }

    // ── 安裝版 ─────────────────────────────────────────────────────────
    private static void applyInstaller(Path installer) throws IOException {
        String name = installer.getFileName().toString().toLowerCase();
        ProcessBuilder pb = name.endsWith(".msi")
                ? new ProcessBuilder("msiexec", "/i", installer.toString())
                : new ProcessBuilder(installer.toString());
        pb.start();
    }

    // ── 免安裝版 ───────────────────────────────────────────────────────
    private static void applyPortable(Path zip) throws IOException {
        Path appDir = AppPaths.appDir();
        Path exe = AppPaths.launcherExe();
        if (appDir == null || exe == null) throw new IOException("Cannot locate application directory");

        Path work = AppPaths.tempDir();
        Path extracted = work.resolve("extracted");
        deleteRecursively(extracted);
        Files.createDirectories(extracted);
        unzip(zip, extracted);

        Path source = findSourceRoot(extracted);
        Path bat = work.resolve("apply-update.bat");
        Files.writeString(bat, buildScript(source, appDir, exe, work), StandardCharsets.UTF_8);

        // 以 wscript 執行 .vbs，並用視窗樣式 0 (隱藏) 啟動批次檔：不會出現 cmd 小黑窗，也不隨本程式結束而中止
        Path vbs = work.resolve("apply-update.vbs");
        writeVbs(vbs, bat);
        new ProcessBuilder("wscript.exe", "//B", "//Nologo", vbs.toString()).start();
    }

    /** 產生隱藏視窗執行批次檔的 VBScript (UTF-16LE + BOM，路徑含中文也能正確讀取) */
    private static void writeVbs(Path vbs, Path bat) throws IOException {
        String cmd = "cmd.exe /c \"\"" + bat + "\"\"";
        String script = "CreateObject(\"WScript.Shell\").Run \"" + cmd.replace("\"", "\"\"") + "\", 0, False\r\n";
        byte[] body = script.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[body.length + 2];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xFE;
        System.arraycopy(body, 0, out, 2, body.length);
        Files.write(vbs, out);
    }

    /** zip 內若只有單一頂層資料夾 (例如 PoeFiveWayScript/)，以該資料夾為來源 */
    private static Path findSourceRoot(Path extracted) throws IOException {
        try (Stream<Path> s = Files.list(extracted)) {
            List<Path> children = s.toList();
            if (children.size() == 1 && Files.isDirectory(children.get(0))) return children.get(0);
        }
        return extracted;
    }

    private static String buildScript(Path src, Path dest, Path exe, Path work) {
        long pid = ProcessHandle.current().pid();
        String nl = "\r\n";
        return "@echo off" + nl
                + "chcp 65001 >nul" + nl
                + "set \"PID=" + pid + "\"" + nl
                + ":wait" + nl
                + "tasklist /FI \"PID eq %PID%\" 2>nul | find \"%PID%\" >nul" + nl
                + "if not errorlevel 1 (" + nl
                + "  ping -n 2 127.0.0.1 >nul" + nl
                + "  goto wait" + nl
                + ")" + nl
                + "robocopy \"" + esc(src) + "\" \"" + esc(dest) + "\" /E /R:10 /W:1"
                + " /XD \"" + esc(dest.resolve("data")) + "\" /NFL /NDL /NJH /NJS /NP >nul" + nl
                + "start \"\" \"" + esc(exe) + "\"" + nl
                + "cd /d \"%TEMP%\"" + nl
                + "rmdir /s /q \"" + esc(work) + "\"" + nl
                + "exit" + nl;
    }

    /** 批次檔中 % 需跳脫為 %% */
    private static String esc(Path p) {
        return p.toString().replace("%", "%%");
    }

    private static void unzip(Path zip, Path targetDir) throws IOException {
        Path root = targetDir.toAbsolutePath().normalize();
        try (InputStream raw = Files.newInputStream(zip); ZipInputStream zin = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("Illegal zip entry: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
