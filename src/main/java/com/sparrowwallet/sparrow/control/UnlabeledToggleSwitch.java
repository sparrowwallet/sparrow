package com.sparrowwallet.sparrow.control;

import impl.org.controlsfx.skin.ToggleSwitchSkin;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;
import org.controlsfx.control.ToggleSwitch;

public class UnlabeledToggleSwitch extends ToggleSwitch {
    public UnlabeledToggleSwitch() {
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        setAccessibleRoleDescription("switch");
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
