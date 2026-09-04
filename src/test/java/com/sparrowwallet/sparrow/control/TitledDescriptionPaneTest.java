package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.JavaFxTestSupport;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TitledDescriptionPaneTest {
    @BeforeAll
    public static void startJavaFx() throws InterruptedException {
        JavaFxTestSupport.startJavaFx();
    }

    @Test
    public void paneExposesHeaderActionsAndExpandedContent() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            TitledDescriptionPane pane = new TitledDescriptionPane("Descriptor", "Wallet import", "Import a wallet descriptor", WalletModel.SPARROW);
            Node originalContent = pane.getContent();

            Assertions.assertEquals(AccessibleRole.PARENT, pane.getAccessibleRole());
            Assertions.assertEquals(AccessibleRole.BUTTON, pane.showHideLink.getAccessibleRole());
            Assertions.assertEquals("Hide details", pane.showHideLink.getText());
            Assertions.assertEquals(List.of(pane.getGraphic(), originalContent), pane.queryAccessibleAttribute(AccessibleAttribute.CHILDREN));

            pane.showHideLink.executeAccessibleAction(AccessibleAction.FIRE);

            Assertions.assertFalse(pane.isExpanded());
            Assertions.assertEquals("Show details", pane.showHideLink.getText());
            Assertions.assertEquals(List.of(pane.getGraphic()), pane.queryAccessibleAttribute(AccessibleAttribute.CHILDREN));

            Node replacementContent = new Pane();
            pane.setContent(replacementContent);
            pane.showHideLink.executeAccessibleAction(AccessibleAction.FIRE);

            Assertions.assertTrue(pane.isExpanded());
            Assertions.assertEquals("Hide details", pane.showHideLink.getText());
            Assertions.assertEquals(List.of(pane.getGraphic(), replacementContent), pane.queryAccessibleAttribute(AccessibleAttribute.CHILDREN));
        });
    }

    @Test
    public void absentHeaderOrContentIsIgnored() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            Node content = new Pane();

            Assertions.assertEquals(List.of(), TitledDescriptionPane.createAccessibleChildren(null, content, false));
            Assertions.assertEquals(List.of(content), TitledDescriptionPane.createAccessibleChildren(null, content, true));
        });
    }
}
