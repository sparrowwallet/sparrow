package com.sparrowwallet.sparrow.control;

import javafx.collections.FXCollections;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * Stack pane for an icon or image that decorates a name or title shown beside it. It is hidden from screen readers, which would
 * otherwise read it as an unnamed image or, for a glyph, as empty text.
 */
public class DecorativePane extends StackPane {
    public DecorativePane(Node... children) {
        super(children);
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch(attribute) {
            case ROLE -> AccessibleRole.NODE;
            case CHILDREN -> FXCollections.emptyObservableList();
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }
}
