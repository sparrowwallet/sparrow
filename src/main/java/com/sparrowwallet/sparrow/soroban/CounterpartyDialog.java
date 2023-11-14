package com.sparrowwallet.sparrow.soroban;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;

public class CounterpartyDialog extends Dialog<Boolean> {
    public CounterpartyDialog(String walletId, Wallet wallet) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader counterpartyLoader = new FXMLLoader(AppServices.class.getResource("soroban/counterparty.fxml"));
            dialogPane.setContent(counterpartyLoader.load());
            CounterpartyController counterpartyController = counterpartyLoader.getController();
            counterpartyController.initializeView(walletId, wallet);

            dialogPane.setPrefWidth(730);
            dialogPane.setPrefHeight(520);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
            dialogPane.getStylesheets().add(AppServices.class.getResource("soroban/counterparty.css").toExternalForm());

            final ButtonType nextButtonType = new javafx.scene.control.ButtonType("Next", ButtonBar.ButtonData.OK_DONE);
            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.APPLY);
            dialogPane.getButtonTypes().addAll(nextButtonType, cancelButtonType, doneButtonType);

            Button nextButton = (Button)dialogPane.lookupButton(nextButtonType);
            Button cancelButton = (Button)dialogPane.lookupButton(cancelButtonType);
            Button doneButton = (Button)dialogPane.lookupButton(doneButtonType);
            doneButton.setDisable(true);
            counterpartyController.meetingReceivedProperty().addListener((observable, oldValue, newValue) -> {
                nextButton.setDisable(newValue != Boolean.TRUE);
            });
            counterpartyController.transactionProperty().addListener((observable, oldValue, newValue) -> {
                nextButton.setVisible(false);
                doneButton.setDisable(newValue == null);
                cancelButton.setDisable(newValue != null);
            });

            nextButton.managedProperty().bind(nextButton.visibleProperty());
            doneButton.managedProperty().bind(doneButton.visibleProperty());

            doneButton.visibleProperty().bind(nextButton.visibleProperty().not());

            nextButton.addEventFilter(ActionEvent.ACTION, event -> {
                if(!counterpartyController.next()) {
                    nextButton.setVisible(false);
                    doneButton.setDefaultButton(true);
                }
                nextButton.setDisable(counterpartyController.meetingReceivedProperty().get() != Boolean.TRUE);
                event.consume();
            });

            cancelButton.addEventFilter(ActionEvent.ACTION, event -> {
                if(counterpartyController.meetingReceivedProperty().get() == Boolean.TRUE) {
                    counterpartyController.cancel();
                }
            });

            setResultConverter(dialogButton -> dialogButton.getButtonData().equals(ButtonBar.ButtonData.APPLY));
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
