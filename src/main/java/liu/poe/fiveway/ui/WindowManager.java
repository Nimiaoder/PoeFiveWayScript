package liu.poe.fiveway.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import liu.poe.fiveway.config.AppConfig;
import liu.poe.fiveway.model.WindowBounds;

import java.util.List;

/**
 * 無邊框視窗的行為：標題列拖曳、邊緣縮放、最大化/還原、透明度、永遠置頂、隱藏/顯示。
 * (JavaFX 的 UNDECORATED 視窗預設不能拖曳/縮放，需自行實作)
 */
public final class WindowManager {

    private static final int EDGE = 6;
    private static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8;

    private final Stage stage;
    private final BooleanProperty maximized = new SimpleBooleanProperty(false);
    private Rectangle2D restoreBounds;

    // 拖曳
    private double dragOffsetX, dragOffsetY;
    // 縮放
    private int resizeEdge = 0;
    private boolean resizing = false;
    private double rsX, rsY, rsW, rsH;

    public WindowManager(Stage stage) {
        this.stage = stage;
    }

    public Stage stage() {
        return stage;
    }

    public BooleanProperty maximizedProperty() {
        return maximized;
    }

    public boolean isMaximized() {
        return maximized.get();
    }

    // ── 初始位置 ───────────────────────────────────────────────────────
    /** 套用儲存的視窗大小/位置；位置不在任何螢幕內時改為置中 */
    public void applyBounds(WindowBounds b) {
        double w = Math.max(AppConfig.MIN_WIDTH, b.width);
        double h = Math.max(AppConfig.MIN_HEIGHT, b.height);
        stage.setWidth(w);
        stage.setHeight(h);
        boolean placed = false;
        if (b.x != null && b.y != null) {
            List<Screen> screens = Screen.getScreensForRectangle(b.x, b.y, w, h);
            if (!screens.isEmpty()) {
                stage.setX(b.x);
                stage.setY(b.y);
                placed = true;
            }
        }
        if (!placed) stage.centerOnScreen();
    }

    // ── 視窗操作 ───────────────────────────────────────────────────────
    public void minimize() {
        stage.setIconified(true);
    }

    public void toggleMaximize() {
        if (maximized.get()) restore();
        else maximize();
    }

    private void maximize() {
        restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        List<Screen> screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        // 使用工作區 (不蓋住工作列)
        Rectangle2D vb = screen.getVisualBounds();
        stage.setX(vb.getMinX());
        stage.setY(vb.getMinY());
        stage.setWidth(vb.getWidth());
        stage.setHeight(vb.getHeight());
        maximized.set(true);
    }

    private void restore() {
        if (restoreBounds != null) {
            stage.setX(restoreBounds.getMinX());
            stage.setY(restoreBounds.getMinY());
            stage.setWidth(restoreBounds.getWidth());
            stage.setHeight(restoreBounds.getHeight());
        }
        maximized.set(false);
    }

    public void setOpacity(double v) {
        stage.setOpacity(v);
    }

    public void setAlwaysOnTop(boolean v) {
        stage.setAlwaysOnTop(v);
    }

    /** 隱藏 / 顯示畫面 (隱藏畫面快捷鍵使用) */
    public void toggleVisibility() {
        if (stage.isShowing()) {
            stage.hide();
        } else {
            showAndFocus();
        }
    }

    public void showAndFocus() {
        if (!stage.isShowing()) stage.show();
        if (stage.isIconified()) stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    // ── 標題列拖曳 ─────────────────────────────────────────────────────
    public void onDragPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragOffsetX = e.getScreenX() - stage.getX();
        dragOffsetY = e.getScreenY() - stage.getY();
    }

    public void onDragged(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) return;
        if (maximized.get()) {
            // 最大化時拖曳：還原視窗，並讓滑鼠維持在標題列相同的相對位置
            double ratio = (e.getScreenX() - stage.getX()) / Math.max(1, stage.getWidth());
            restore();
            dragOffsetX = stage.getWidth() * ratio;
        }
        stage.setX(e.getScreenX() - dragOffsetX);
        stage.setY(e.getScreenY() - dragOffsetY);
    }

    // ── 邊緣縮放 ───────────────────────────────────────────────────────
    public void installResize(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (maximized.get()) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }
            scene.setCursor(cursorFor(edgeAt(scene, e)));
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (maximized.get() || e.getButton() != MouseButton.PRIMARY) return;
            int edge = edgeAt(scene, e);
            if (edge == 0) return;
            resizing = true;
            resizeEdge = edge;
            rsX = stage.getX();
            rsY = stage.getY();
            rsW = stage.getWidth();
            rsH = stage.getHeight();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!resizing) return;
            doResize(e);
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizing) {
                resizing = false;
                e.consume();
            }
        });
    }

    private int edgeAt(Scene scene, MouseEvent e) {
        int edge = 0;
        if (e.getSceneX() < EDGE) edge |= LEFT;
        else if (e.getSceneX() > scene.getWidth() - EDGE) edge |= RIGHT;
        if (e.getSceneY() < EDGE) edge |= TOP;
        else if (e.getSceneY() > scene.getHeight() - EDGE) edge |= BOTTOM;
        return edge;
    }

    private Cursor cursorFor(int edge) {
        return switch (edge) {
            case LEFT, RIGHT -> Cursor.H_RESIZE;
            case TOP, BOTTOM -> Cursor.V_RESIZE;
            case LEFT | TOP, RIGHT | BOTTOM -> Cursor.NW_RESIZE;
            case RIGHT | TOP, LEFT | BOTTOM -> Cursor.NE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    private void doResize(MouseEvent e) {
        double sx = e.getScreenX();
        double sy = e.getScreenY();
        double x = rsX, y = rsY, w = rsW, h = rsH;
        if ((resizeEdge & LEFT) != 0) {
            double nw = Math.max(AppConfig.MIN_WIDTH, rsX + rsW - sx);
            x = rsX + rsW - nw;
            w = nw;
        }
        if ((resizeEdge & RIGHT) != 0) {
            w = Math.max(AppConfig.MIN_WIDTH, sx - rsX);
        }
        if ((resizeEdge & TOP) != 0) {
            double nh = Math.max(AppConfig.MIN_HEIGHT, rsY + rsH - sy);
            y = rsY + rsH - nh;
            h = nh;
        }
        if ((resizeEdge & BOTTOM) != 0) {
            h = Math.max(AppConfig.MIN_HEIGHT, sy - rsY);
        }
        stage.setX(x);
        stage.setY(y);
        stage.setWidth(w);
        stage.setHeight(h);
    }
}
