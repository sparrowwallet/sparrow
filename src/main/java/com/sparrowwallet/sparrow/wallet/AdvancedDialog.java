package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.controlsfx.tools.Borders;

import java.io.IOException;

public class AdvancedDialog extends Dialog<Boolean> {
    public AdvancedDialog(WalletForm walletForm) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        Wallet wallet = walletForm.getWallet();

        try {
            FXMLLoader advancedLoader = new FXMLLoader(AppServices.class.getResource("wallet/advanced.fxml"));
            dialogPane.setContent(Borders.wrap(advancedLoader.load()).emptyBorder().buildAll());
            AdvancedController settingsAdvancedController = advancedLoader.getController();
            settingsAdvancedController.initializeView(wallet);

            boolean noPassword = Storage.NO_PASSWORD_KEY.equals(walletForm.getStorage().getEncryptionPubKey());
            final ButtonType closeButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType passwordButtonType = new javafx.scene.control.ButtonType(noPassword ? "Add Password..." : "Change Password...", ButtonBar.ButtonData.LEFT);
            dialogPane.getButtonTypes().add(closeButtonType);
            if(wallet.isValid()) {
                dialogPane.getButtonTypes().add(passwordButtonType);
            }

            dialogPane.setPrefWidth(400);
            dialogPane.setPrefHeight(300);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            setOnCloseRequest(event -> {
                settingsAdvancedController.close();
            });

            setResultConverter(dialogButton -> dialogButton == passwordButtonType);
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
