package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.ScriptChunk;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.TwoDimensional;

import java.time.Duration;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;

public class TextDecoration extends StackPane {
    private StringProperty label = new SimpleStringProperty();

    private Rectangle rectangle;
    private Text text;

    public TextDecoration(String label, String description, String styleClass) {
        rectangle = new Rectangle();
        rectangle.setArcHeight(10);
        rectangle.setArcWidth(10);
        rectangle.getStyleClass().add("text-decoration-box");

        DropShadow drop = new DropShadow();
        drop.setWidth(2);
        drop.setHeight(2);
        drop.setOffsetX(1);
        drop.setOffsetY(1);
        drop.setRadius(2);
        rectangle.setEffect(drop);

        text = new Text(label);
        text.setFont(Font.getDefault());
        text.getStyleClass().add("text-decoration-label");

        if(styleClass != null) {
            rectangle.getStyleClass().add(styleClass);
        }

        getChildren().addAll(rectangle, text);

        this.labelProperty().addListener((ob, o, n) -> {
            // expand the rectangle
            double width = TextUtils.computeTextWidth(text.getFont(), this.getLabel(), 0.0D) + 2;
            rectangle.setWidth(width);
            rectangle.setHeight(text.getFont().getSize());
        });

        setLabel(label);

        Popup popup = new Popup();
        Label popupMsg = new Label();
        popupMsg.getStyleClass().add("tooltip");
        popup.getContent().add(popupMsg);

        text.setOnMouseEntered(event -> {
            popupMsg.setText(description);
            popup.show(this, event.getScreenX(), event.getScreenY() + 10);
        });
        text.setOnMouseExited(event -> {
            popup.hide();
        });
        text.setCursor(Cursor.DEFAULT);
    }

    public final StringProperty labelProperty() {
        return label;
    }

    public final String getLabel() {
        return label.get();
    }

    public final void setLabel(String value) {
        this.label.set(value);
    }
}
