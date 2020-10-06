package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.controlsfx.glyphfont.Glyph;

public class HelpLabel extends Label {
    private final Tooltip tooltip;

    public HelpLabel() {
        super("", getHelpGlyph());
        tooltip = new Tooltip();
        tooltip.textProperty().bind(helpTextProperty());
        setTooltip(tooltip);
        getStyleClass().add("help-label");
    }

    private static Glyph getHelpGlyph() {
        Glyph lockGlyph = new Glyph("Font Awesome 5 Free Solid", FontAwesome5.Glyph.QUESTION_CIRCLE);
        lockGlyph.getStyleClass().add("help-icon");
        lockGlyph.setFontSize(12);
        return lockGlyph;
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
}
