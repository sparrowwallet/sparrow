package com.sparrowwallet.sparrow.paynym;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.io.IOException;

public class PayNymAddressesDialog extends Dialog<Boolean> {
    public PayNymAddressesDialog(WalletForm walletForm) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader payNymLoader = new FXMLLoader(AppServices.class.getResource("paynym/paynymaddresses.fxml"));
            dialogPane.setContent(payNymLoader.load());
            PayNymAddressesController controller = payNymLoader.getController();
            controller.initializeView(walletForm);

            EventManager.get().register(controller);

            final ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.OK_DONE);
            dialogPane.getButtonTypes().add(doneButtonType);

            setOnCloseRequest(event -> {
                EventManager.get().unregister(controller);
            });

            setResultConverter(dialogButton -> dialogButton == doneButtonType ? Boolean.TRUE : Boolean.FALSE);

            dialogPane.setPrefWidth(800);
            dialogPane.setPrefHeight(600);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());

            setResizable(true);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
