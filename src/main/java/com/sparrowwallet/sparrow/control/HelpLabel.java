package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;

public class HelpLabel extends Label {
    private final Tooltip tooltip;

    public HelpLabel() {
        super("", getHelpGlyph());
        tooltip = new Tooltip();
        tooltip.textProperty().bind(helpTextProperty());
        tooltip.graphicProperty().bind(helpGraphicProperty());
        tooltip.setShowDuration(Duration.seconds(15));
        tooltip.setShowDelay(Duration.millis(500));
        getStyleClass().add("help-label");

        Platform.runLater(() -> setTooltip(tooltip));
    }

    private static Glyph getHelpGlyph() {
        Glyph glyph = new Glyph("Font Awesome 5 Free Solid", FontAwesome5.Glyph.QUESTION_CIRCLE);
        glyph.getStyleClass().add("help-icon");
        glyph.setFontSize(11);
        return glyph;
    }

    public final StringProperty helpTextProperty() {
        if(helpText == null) {
            helpText = new SimpleStringProperty(this, "helpText", "");
        }

        return helpText;
    }

    private StringProperty helpText;

    public final void setHelpText(String value) {
        helpTextProperty().setValue(value.replace("\\n", "\n"));
    }

    public final String getHelpText() {
        return helpText == null ? "" : helpText.getValue();
    }

    public ObjectProperty<Node> helpGraphicProperty() {
        if(helpGraphicProperty == null) {
            helpGraphicProperty = new SimpleObjectProperty<Node>(this, "helpGraphic", null);
        }

        return helpGraphicProperty;
    }

    private ObjectProperty<Node> helpGraphicProperty;

    public final void setHelpGraphic(Node graphic) {
        helpGraphicProperty().setValue(graphic);
    }
}
