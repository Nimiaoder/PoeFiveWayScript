package liu.poe.fiveway.ui.component;

import javafx.animation.RotateTransition;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * 可展開/收起的區塊卡片 (對應原版 Section)。
 * 點擊整條標題列切換，右側 extra 區 (按鈕、狀態徽章) 的點擊不會觸發展開/收起。
 * <p>
 * FXML 用法：
 * <pre>
 * &lt;Section title="%section.xxx"&gt;
 *     &lt;extra&gt;&lt;Button .../&gt;&lt;/extra&gt;
 *     ...內容...
 * &lt;/Section&gt;
 * </pre>
 */
@DefaultProperty("content")
public class Section extends VBox {

    private static final PseudoClass OPEN = PseudoClass.getPseudoClass("open");

    private final StringProperty title = new SimpleStringProperty("");
    private final BooleanProperty open = new SimpleBooleanProperty(true);

    private final HBox header = new HBox(8);
    private final SVGPath chevron = new SVGPath();
    private final Label titleLabel = new Label();
    private final StackPane extraHolder = new StackPane();
    private final VBox body = new VBox();
    private final RotateTransition rotate = new RotateTransition(Duration.millis(180), chevron);

    public Section() {
        getStyleClass().addAll("card", "section");

        chevron.setContent("M4 2.5 L8 6 L4 9.5");
        chevron.getStyleClass().add("section-chev");

        titleLabel.getStyleClass().add("section-title");
        titleLabel.textProperty().bind(title);

        Label spacer = new Label();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMaxWidth(Double.MAX_VALUE);

        extraHolder.getStyleClass().add("section-extra");
        extraHolder.setVisible(false);
        extraHolder.setManaged(false);
        // 點擊 extra 區不觸發展開/收起
        extraHolder.addEventHandler(MouseEvent.MOUSE_CLICKED, Event::consume);

        header.getStyleClass().add("section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setCursor(Cursor.HAND);
        header.getChildren().addAll(chevron, titleLabel, spacer, extraHolder);
        header.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) open.set(!open.get());
        });

        body.getStyleClass().add("section-body");
        body.setSpacing(0);

        getChildren().addAll(header, body);

        open.addListener((o, ov, nv) -> applyOpen(nv, true));
        applyOpen(true, false);
    }

    private void applyOpen(boolean isOpen, boolean animate) {
        pseudoClassStateChanged(OPEN, isOpen);
        header.pseudoClassStateChanged(OPEN, isOpen);
        body.setVisible(isOpen);
        body.setManaged(isOpen);
        double angle = isOpen ? 90 : 0;
        if (animate) {
            rotate.stop();
            rotate.setToAngle(angle);
            rotate.play();
        } else {
            chevron.setRotate(angle);
        }
    }

    // ── FXML 屬性 ─────────────────────────────────────────────────────
    public String getTitle() {
        return title.get();
    }

    public void setTitle(String t) {
        title.set(t);
    }

    public StringProperty titleProperty() {
        return title;
    }

    public boolean isOpen() {
        return open.get();
    }

    public void setOpen(boolean v) {
        open.set(v);
    }

    public BooleanProperty openProperty() {
        return open;
    }

    /** 標題列右側的額外元素 */
    public Node getExtra() {
        return extraHolder.getChildren().isEmpty() ? null : extraHolder.getChildren().get(0);
    }

    public void setExtra(Node node) {
        extraHolder.getChildren().clear();
        if (node != null) {
            extraHolder.getChildren().add(node);
        }
        extraHolder.setVisible(node != null);
        extraHolder.setManaged(node != null);
    }

    /** 區塊內容 (FXML 預設屬性) */
    public ObservableList<Node> getContent() {
        return body.getChildren();
    }
}
