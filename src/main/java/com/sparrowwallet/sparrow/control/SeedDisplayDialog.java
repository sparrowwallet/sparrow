package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

public class SeedDisplayDialog extends Dialog<Void> {
    public SeedDisplayDialog(Keystore decryptedKeystore) {
        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        int lines = decryptedKeystore.getSeed().getMnemonicCode().size() / 3;
        int height = lines * 40;

        StackPane stackPane = new StackPane();
        dialogPane.setContent(stackPane);

        AnchorPane anchorPane = new AnchorPane();
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("edge-to-edge");
        scrollPane.setPrefHeight(74 + height);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        Accordion keystoreAccordion = new Accordion();
        scrollPane.setContent(keystoreAccordion);

        MnemonicKeystoreDisplayPane keystorePane = new MnemonicKeystoreDisplayPane(decryptedKeystore);
        keystorePane.setAnimated(false);
        keystoreAccordion.getPanes().add(keystorePane);

        stackPane.getChildren().addAll(anchorPane);

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);

        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(150 + height);
        AppServices.moveToActiveWindowScreen(this);

        Platform.runLater(() -> keystoreAccordion.setExpandedPane(keystorePane));
    }
}
