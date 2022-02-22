package com.sparrowwallet.sparrow.soroban;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;

public class PayNymDialog extends Dialog<PayNym> {
    public PayNymDialog(String walletId) {
        this(walletId, false, false);
    }

    public PayNymDialog(String walletId, boolean selectPayNym, boolean selectLinkedOnly) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader payNymLoader = new FXMLLoader(AppServices.class.getResource("soroban/paynym.fxml"));
            dialogPane.setContent(payNymLoader.load());
            PayNymController payNymController = payNymLoader.getController();
            payNymController.initializeView(walletId, selectLinkedOnly);

            EventManager.get().register(payNymController);

            dialogPane.setPrefWidth(730);
            dialogPane.setPrefHeight(600);
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
            dialogPane.getStylesheets().add(AppServices.class.getResource("soroban/paynym.css").toExternalForm());

            final ButtonType selectButtonType = new javafx.scene.control.ButtonType("Select Contact", ButtonBar.ButtonData.APPLY);
            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.OK_DONE);

            if(selectPayNym) {
                dialogPane.getButtonTypes().addAll(selectButtonType, cancelButtonType);
                Button selectButton = (Button)dialogPane.lookupButton(selectButtonType);
                selectButton.setDisable(true);
                selectButton.setDefaultButton(true);
                payNymController.payNymProperty().addListener((observable, oldValue, payNym) -> {
                    selectButton.setDisable(payNym == null || (selectLinkedOnly && !payNymController.isLinked(payNym)));
                });
            } else {
                dialogPane.getButtonTypes().add(doneButtonType);
            }

            setOnCloseRequest(event -> {
                EventManager.get().unregister(payNymController);
            });

            setResultConverter(dialogButton -> dialogButton == selectButtonType ? payNymController.getPayNym() : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
