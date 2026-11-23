package liu.poe.fiveway.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import liu.poe.fiveway.config.AppConfig;
import liu.poe.fiveway.config.AppPaths;
import liu.poe.fiveway.i18n.I18n;
import liu.poe.fiveway.model.Settings;
import liu.poe.fiveway.model.WindowBounds;
import liu.poe.fiveway.ui.MainController;
import liu.poe.fiveway.ui.WindowManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** JavaFX 應用程式入口：建立無邊框視窗、載入 main.fxml、管理生命週期 */
public class PoeFiveWayApp extends Application implements AppHost {
    private static final Logger log = LoggerFactory.getLogger(PoeFiveWayApp.class);

    private static final String CSS = "/css/dark-gray.css";
    private static final String ICON = "/icon/app/icon.png";

    private AppContext ctx;
    private Stage stage;
    private Scene scene;
    private WindowManager window;
    private MainController main;
    private final SingleInstanceGuard guard = new SingleInstanceGuard();

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        log.info("{} v{} 啟動 (distribution={}, dataRoot={})",
                AppConfig.appName(), AppConfig.appVersion(), AppConfig.distribution(), AppPaths.dataRoot());
        ctx = AppContext.create();
        I18n.init(ctx.settings().language);

        // 單一實例鎖定：已有實例在執行 → 通知它並結束
        boolean first = guard.tryAcquire(AppConfig.singleInstancePort(),
                () -> Platform.runLater(this::onSecondInstance));
        if (!first) {
            log.info("偵測到已有執行中的實例，結束本次啟動");
            Platform.exit();
            System.exit(0);
            return;
        }

        // 隱藏視窗(隱藏畫面快捷鍵)時不能讓程式結束
        Platform.setImplicitExit(false);

        stage.initStyle(StageStyle.UNDECORATED);   // 自訂 titlebar
        stage.setTitle(AppConfig.appName() + " v" + AppConfig.appVersion());
        applyIcon();
        stage.setMinWidth(AppConfig.MIN_WIDTH);
        stage.setMinHeight(AppConfig.MIN_HEIGHT);

        window = new WindowManager(stage);
        ctx.setWindow(window);
        ctx.setHost(this);

        Parent root = loadRoot(0);
        scene = new Scene(root);
        scene.setFill(Color.web("#121212"));
        scene.getStylesheets().add(getClass().getResource(CSS).toExternalForm());
        stage.setScene(scene);

        Settings s = ctx.settings();
        window.applyBounds(s.windowBounds);
        stage.setOpacity(s.opacity);
        stage.setAlwaysOnTop(s.alwaysOnTop);
        window.installResize(scene);
        stage.setOnCloseRequest(e -> {
            e.consume();
            exitApp();
        });

        stage.show();
        trackBounds();
        ctx.rebindHotkeys();

        if (AppConfig.checkUpdateOnStartup()) {
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(e -> ctx.updateFlow().check(false, null));
            delay.play();
        }
    }

    /** 視窗 / 工作列圖標 (JavaFX 不吃 .ico，改用同目錄的 icon.png；exe 的圖標由 jpackage 吃 icon.ico) */
    private void applyIcon() {
        try (InputStream in = getClass().getResourceAsStream(ICON)) {
            if (in != null) stage.getIcons().add(new Image(in));
            else log.warn("找不到視窗圖標：{}", ICON);
        } catch (IOException e) {
            log.warn("載入視窗圖標失敗", e);
        }
    }

    private Parent loadRoot(int tabIndex) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"), I18n.bundle());
            Parent root = loader.load();
            main = loader.getController();
            ctx.clearListeners();
            main.init(tabIndex);
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 視窗大小/位置自動儲存 (最大化、最小化時不記錄) */
    private void trackBounds() {
        ChangeListener<Number> l = (o, a, b) -> {
            if (!stage.isShowing() || stage.isIconified() || window.isMaximized()) return;
            ctx.updateQuiet(s -> {
                if (s.windowBounds == null) s.windowBounds = new WindowBounds();
                s.windowBounds.x = stage.getX();
                s.windowBounds.y = stage.getY();
                s.windowBounds.width = stage.getWidth();
                s.windowBounds.height = stage.getHeight();
            });
        };
        stage.xProperty().addListener(l);
        stage.yProperty().addListener(l);
        stage.widthProperty().addListener(l);
        stage.heightProperty().addListener(l);
    }

    /**
     * 第二次開啟程式時觸發：
     * - 視窗處於隱藏狀態 → 直接顯示視窗 (不彈提示)
     * - 視窗已顯示中 → 彈出提示訊息
     */
    private void onSecondInstance() {
        if (stage == null || window == null) return;
        if (!stage.isShowing()) {
            window.showAndFocus();
            return;
        }
        window.showAndFocus();
        if (ctx.ui() != null) {
            ctx.ui().showMessage(I18n.t("dialog.title.hint"), I18n.t("dialog.alreadyRunning"));
        }
    }

    // ── AppHost ────────────────────────────────────────────────────────
    @Override
    public void rebuildUi() {
        int tab = main != null ? main.selectedTab() : 0;
        scene.setRoot(loadRoot(tab));
    }

    @Override
    public void exitApp() {
        log.info("使用者結束程式");
        guard.close();
        ctx.shutdown();
        Platform.exit();
        System.exit(0);
    }

    @Override
    public void openExternal(String url) {
        getHostServices().showDocument(url);
    }
}
