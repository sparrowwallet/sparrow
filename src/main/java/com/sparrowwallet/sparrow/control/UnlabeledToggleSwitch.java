package com.sparrowwallet.sparrow.control;

import impl.org.controlsfx.skin.ToggleSwitchSkin;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Parent;
import javafx.scene.control.Skin;
import org.controlsfx.control.ToggleSwitch;
import tornadofx.control.Field;

public class UnlabeledToggleSwitch extends ToggleSwitch {
    public UnlabeledToggleSwitch() {
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        selectedProperty().addListener((observable, oldValue, newValue) -> notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED));
    }

    @Override protected Skin<?> createDefaultSkin() {
        return new ToggleSwitchSkin(this) {
            @Override
            protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
                return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) - 20;
            }
        };
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if(attribute == AccessibleAttribute.SELECTED) {
            return isSelected();
        } else if(attribute == AccessibleAttribute.TEXT) {
            Object text = super.queryAccessibleAttribute(attribute, parameters);
            if(text instanceof String str && !str.isEmpty()) {
                return text;
            }

            //Without its own text, the switch is named by the form field it sits in
            for(Parent parent = getParent(); parent != null; parent = parent.getParent()) {
                if(parent instanceof Field field && field.getText() != null) {
                    return field.getText().replaceAll(":$", "");
                }
            }
        }

        return super.queryAccessibleAttribute(attribute, parameters);
    }

    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if(action == AccessibleAction.FIRE) {
            fire();
        } else {
            super.executeAccessibleAction(action, parameters);
        }
    }
}
