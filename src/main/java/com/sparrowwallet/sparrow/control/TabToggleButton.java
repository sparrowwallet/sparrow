package com.sparrowwallet.sparrow.control;

import javafx.scene.AccessibleRole;
import javafx.scene.control.ToggleButton;

/**
 * A toggle button used to select a page in a visually custom tab interface.
 */
public class TabToggleButton extends ToggleButton {
    public TabToggleButton() {
        setAccessibleRole(AccessibleRole.TAB_ITEM);
    }
}
