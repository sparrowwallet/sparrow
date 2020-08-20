package com.sparrowwallet.sparrow.keystoreimport;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
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
import java.util.List;

public class KeystoreImportDialog extends Dialog<Keystore> {
    private final KeystoreImportController keystoreImportController;
    private Keystore keystore;

    public KeystoreImportDialog(Wallet wallet) {
        this(wallet, KeystoreSource.HW_USB);
    }

    public KeystoreImportDialog(Wallet wallet, KeystoreSource initialSource) {
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
        });

        final DialogPane dialogPane = getDialogPane();

        try {
            FXMLLoader ksiLoader = new FXMLLoader(AppController.class.getResource("keystoreimport/keystoreimport.fxml"));
            dialogPane.setContent(Borders.wrap(ksiLoader.load()).lineBorder().outerPadding(0).innerPadding(0).buildAll());
            keystoreImportController = ksiLoader.getController();
            keystoreImportController.initializeView(wallet);
            keystoreImportController.selectSource(initialSource);

            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(cancelButtonType);
            dialogPane.setPrefWidth(650);
            dialogPane.setPrefHeight(620);

            setResultConverter(dialogButton -> dialogButton != cancelButtonType ? keystore : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<KeystoreSource> getSupportedSources() {
        return List.of(KeystoreSource.HW_USB, KeystoreSource.HW_AIRGAPPED, KeystoreSource.SW_SEED);
    }

    @Subscribe
    public void keystoreImported(KeystoreImportEvent event) {
        this.keystore = event.getKeystore();
        setResult(keystore);
    }
}
