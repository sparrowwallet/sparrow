package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.collections.FXCollections;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import org.fxmisc.richtext.CodeArea;

/**
 * Code area for displaying descriptors, scripts and transactions. RichTextFX areas expose nothing to screen readers, so focus on
 * one is silent; this one is read as a read-only text area holding its text, and Tab moves focus on rather than being swallowed.
 */
public class ReadOnlyCodeArea extends CodeArea {
    public ReadOnlyCodeArea() {
        setAccessibleRole(AccessibleRole.TEXT_AREA);
        AppServices.moveFocusOnTab(this);
        //Text changes only: the text property is also invalidated by style changes, and reading it rebuilds the whole text
        plainTextChanges().subscribe(_ -> notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT));
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch(attribute) {
            case TEXT -> getText();
            case EDITABLE -> isEditable();
            //The styled text segments would otherwise be read as separate pieces of text
            case CHILDREN -> FXCollections.emptyObservableList();
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }
}
