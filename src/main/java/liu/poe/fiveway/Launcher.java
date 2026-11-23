package liu.poe.fiveway;

import javafx.application.Application;
import liu.poe.fiveway.app.PoeFiveWayApp;
import liu.poe.fiveway.config.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 實際的程式進入點。
 * <p>
 * 以「非模組化」方式打包時，主類別不能直接繼承 {@link Application}，
 * 否則 JavaFX 會誤判而出現「缺少 JavaFX runtime components」錯誤，因此多包一層。
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        // 必須在第一次取得 Logger 之前設定：logback.xml 以 ${app.data.dir} 決定 logs.log 位置
        System.setProperty("app.data.dir", AppPaths.dataRoot().toString());

        Logger log = LoggerFactory.getLogger(Launcher.class);
        Thread.setDefaultUncaughtExceptionHandler(
                (t, e) -> log.error("未捕捉的例外 (thread={})", t.getName(), e));

        Application.launch(PoeFiveWayApp.class, args);
    }
}
