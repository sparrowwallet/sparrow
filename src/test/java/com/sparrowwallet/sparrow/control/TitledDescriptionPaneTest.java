package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletModel;
import javafx.application.Platform;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class TitledDescriptionPaneTest {
    private static String glassPlatform;

    @BeforeAll
    public static void startJavaFx() throws InterruptedException {
        glassPlatform = System.getProperty("glass.platform");
        System.setProperty("glass.platform", "Headless");

        CountDownLatch startupLatch = new CountDownLatch(1);
        Platform.startup(startupLatch::countDown);
        Assertions.assertTrue(startupLatch.await(10, TimeUnit.SECONDS));
    }

    @AfterAll
    public static void stopJavaFx() {
        Platform.exit();
        if(glassPlatform == null) {
            System.clearProperty("glass.platform");
        } else {
            System.setProperty("glass.platform", glassPlatform);
        }
    }

    @Test
    public void paneExposesHeaderActionsAndExpandedContent() throws Exception {
        runOnFxThread(() -> {
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
        runOnFxThread(() -> {
            Node content = new Pane();

            Assertions.assertEquals(List.of(), TitledDescriptionPane.createAccessibleChildren(null, content, false));
            Assertions.assertEquals(List.of(content), TitledDescriptionPane.createAccessibleChildren(null, content, true));
        });
    }

    private static void runOnFxThread(Runnable runnable) throws Exception {
        FutureTask<Void> task = new FutureTask<>(runnable, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
