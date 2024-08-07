package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.SeedQR;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;

import java.util.Optional;

public class SeedDisplayDialog extends Dialog<Void> {
    public SeedDisplayDialog(Keystore decryptedKeystore) {
        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        int lines = Math.ceilDiv(decryptedKeystore.getSeed().getMnemonicCode().size(), 3);
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

        if(decryptedKeystore.getSeed().getType() == DeterministicSeed.Type.BIP39) {
            final ButtonType seedQRButtonType = new javafx.scene.control.ButtonType("Show SeedQR", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(seedQRButtonType);

            Button seedQRButton = (Button)dialogPane.lookupButton(seedQRButtonType);
            seedQRButton.addEventFilter(ActionEvent.ACTION, event -> {
                event.consume();
                showSeedQR(decryptedKeystore);
            });
        }

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(cancelButtonType);

        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(150 + height);
        AppServices.moveToActiveWindowScreen(this);

        Platform.runLater(() -> keystoreAccordion.setExpandedPane(keystorePane));
    }

    private void showSeedQR(Keystore decryptedKeystore) {
        Optional<ButtonType> optButtonType = AppServices.showWarningDialog("Sensitive QR", "The following QR contains these seed words. " +
                "Be careful before displaying or digitally recording it.\n\nAre you sure you want to continue?", ButtonType.YES, ButtonType.NO);
        if(optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
            String seedQR = SeedQR.getSeedQR(decryptedKeystore.getSeed());
            QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(seedQR);
            qrDisplayDialog.initOwner(getDialogPane().getScene().getWindow());
            qrDisplayDialog.showAndWait();
        }
    }
}
