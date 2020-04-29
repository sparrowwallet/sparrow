package com.sparrowwallet.sparrow.keystoreimport;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.controlsfx.tools.Borders;

import java.io.IOException;

public class KeystoreImportDialog extends Dialog<Keystore> {
    private final KeystoreImportController keystoreImportController;
    private Keystore keystore;

    public KeystoreImportDialog(Wallet wallet) {
        EventManager.get().register(this);
        final DialogPane dialogPane = getDialogPane();

        try {
            FXMLLoader ksiLoader = new FXMLLoader(AppController.class.getResource("keystoreimport/keystoreimport.fxml"));
            dialogPane.setContent(Borders.wrap(ksiLoader.load()).lineBorder().outerPadding(0).innerPadding(0).buildAll());
            keystoreImportController = ksiLoader.getController();
            keystoreImportController.initializeView(wallet);

            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(cancelButtonType);
            dialogPane.setPrefWidth(620);
            dialogPane.setPrefHeight(500);

            setResultConverter(dialogButton -> dialogButton != cancelButtonType ? keystore : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Subscribe
    public void keystoreImported(KeystoreImportEvent event) {
        this.keystore = event.getKeystore();
        System.out.println(keystore.getLabel() + " " + keystore.getKeyDerivation().getMasterFingerprint());
        this.close();
    }
}
