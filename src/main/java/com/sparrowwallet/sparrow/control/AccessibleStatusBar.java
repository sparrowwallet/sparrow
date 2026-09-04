package com.sparrowwallet.sparrow.control;

import javafx.scene.AccessibleRole;
import org.controlsfx.control.StatusBar;

public class AccessibleStatusBar extends StatusBar {
    public AccessibleStatusBar() {
        setAccessibleRole(AccessibleRole.TOOL_BAR);
        setAccessibleRoleDescription("status bar");
    }
}
