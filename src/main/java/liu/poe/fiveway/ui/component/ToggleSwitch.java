package liu.poe.fiveway.ui.component;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * 開關元件 (對應原版 Switch)。
 * 只有「使用者點擊」才會觸發 onAction；程式呼叫 setSelected 不會觸發，避免回圈。
 */
public class ToggleSwitch extends StackPane {

    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new SimpleObjectProperty<>();
    private final Circle thumb = new Circle(7);
    private final TranslateTransition slide = new TranslateTransition(Duration.millis(200), thumb);

    public ToggleSwitch() {
        getStyleClass().add("toggle-switch");
        thumb.getStyleClass().add("toggle-thumb");
        setAlignment(Pos.CENTER_LEFT);
        setMinSize(40, 20);
        setPrefSize(40, 20);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setCursor(Cursor.HAND);
        getChildren().add(thumb);
        thumb.setTranslateX(3);

        selected.addListener((o, ov, nv) -> {
            pseudoClassStateChanged(ON, nv);
            slide.stop();
            slide.setToX(nv ? 23 : 3);
            slide.play();
        });

        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || isDisabled()) return;
            setSelected(!isSelected());
            EventHandler<ActionEvent> h = getOnAction();
            if (h != null) h.handle(new ActionEvent(this, this));
        });
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean v) {
        selected.set(v);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    public void setOnAction(EventHandler<ActionEvent> h) {
        onAction.set(h);
    }

    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }
}
