package liu.poe.fiveway;

import liu.poe.fiveway.app.PoeFiveWayApp;
import javafx.application.Application;

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
        Application.launch(PoeFiveWayApp.class, args);
    }
}
