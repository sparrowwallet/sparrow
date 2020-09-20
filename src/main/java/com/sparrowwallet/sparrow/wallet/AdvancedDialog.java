package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.controlsfx.tools.Borders;

import java.io.IOException;

public class AdvancedDialog extends Dialog<Void> {
    public AdvancedDialog(Wallet wallet) {
        final DialogPane dialogPane = getDialogPane();
        AppController.setStageIcon(dialogPane.getScene().getWindow());

        try {
            FXMLLoader advancedLoader = new FXMLLoader(AppController.class.getResource("wallet/advanced.fxml"));
            dialogPane.setContent(Borders.wrap(advancedLoader.load()).emptyBorder().buildAll());
            AdvancedController settingsAdvancedController = advancedLoader.getController();
            settingsAdvancedController.initializeView(wallet);

            final ButtonType closeButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(closeButtonType);

            dialogPane.setPrefWidth(400);
            dialogPane.setPrefHeight(300);
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
