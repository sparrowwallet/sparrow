package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.OsType;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AppServicesTest {
    @Test
    public void dialogRoleIsExposedAsParentOnMacOs() {
        Pane root = new Pane();
        root.setAccessibleRole(AccessibleRole.DIALOG);

        AppServices.configureDialogAccessibility(root, OsType.MACOS);

        Assertions.assertEquals(AccessibleRole.PARENT, root.getAccessibleRole());
    }

    @Test
    public void nonDialogRoleIsUnchangedOnMacOs() {
        Pane root = new Pane();
        root.setAccessibleRole(AccessibleRole.NODE);

        AppServices.configureDialogAccessibility(root, OsType.MACOS);

        Assertions.assertEquals(AccessibleRole.NODE, root.getAccessibleRole());
    }

    @Test
    public void dialogRoleIsUnchangedOnOtherPlatforms() {
        for(OsType osType : new OsType[] {OsType.WINDOWS, OsType.UNIX, OsType.UNKNOWN}) {
            Pane root = new Pane();
            root.setAccessibleRole(AccessibleRole.DIALOG);

            AppServices.configureDialogAccessibility(root, osType);

            Assertions.assertEquals(AccessibleRole.DIALOG, root.getAccessibleRole(), osType.getPlatformId());
        }
    }
}
