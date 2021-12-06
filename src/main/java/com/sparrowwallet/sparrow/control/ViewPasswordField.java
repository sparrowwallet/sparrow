package com.sparrowwallet.sparrow.control;

import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import org.controlsfx.control.textfield.CustomPasswordField;

public class ViewPasswordField extends CustomPasswordField {
    public ViewPasswordField() {
        super();
        getStyleClass().add("view-password-text-field");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ViewPasswordFieldSkin(this) {
            @Override
            public ObjectProperty<Node> leftProperty() {
                return ViewPasswordField.this.leftProperty();
            }

            @Override
            public ObjectProperty<Node> rightProperty() {
                return ViewPasswordField.this.rightProperty();
            }
        };
    }
}
