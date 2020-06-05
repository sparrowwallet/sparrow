package com.sparrowwallet.sparrow.control;

import impl.org.controlsfx.skin.ToggleSwitchSkin;
import javafx.scene.control.Skin;
import org.controlsfx.control.ToggleSwitch;

public class UnlabeledToggleSwitch extends ToggleSwitch {
    @Override protected Skin<?> createDefaultSkin() {
        return new ToggleSwitchSkin(this) {
            @Override
            protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
                return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset) - 20;
            }
        };
    }
}
