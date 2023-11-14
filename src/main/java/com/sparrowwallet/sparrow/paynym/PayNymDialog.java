package com.sparrowwallet.sparrow.paynym;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;

public class PayNymDialog extends Dialog<PayNym> {
    public PayNymDialog(String walletId) {
        this(walletId, Operation.SHOW, false);
    }

    public PayNymDialog(String walletId, Operation operation, boolean selectLinkedOnly) {
        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader payNymLoader = new FXMLLoader(AppServices.class.getResource("paynym/paynym.fxml"));
            dialogPane.setContent(payNymLoader.load());
            PayNymController payNymController = payNymLoader.getController();
            payNymController.initializeView(walletId, selectLinkedOnly);

            EventManager.get().register(payNymController);

            dialogPane.setPrefWidth(730);
            dialogPane.setPrefHeight(600);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
            dialogPane.getStylesheets().add(AppServices.class.getResource("paynym/paynym.css").toExternalForm());

            final ButtonType sendDirectlyButtonType = new javafx.scene.control.ButtonType("Send Directly", ButtonBar.ButtonData.APPLY);
            final ButtonType sendCollaborativelyButtonType = new javafx.scene.control.ButtonType("Send Collaboratively", ButtonBar.ButtonData.OK_DONE);
            final ButtonType selectButtonType = new javafx.scene.control.ButtonType("Select Contact", ButtonBar.ButtonData.APPLY);
            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.OK_DONE);

            if(operation == Operation.SEND) {
                if(selectLinkedOnly) {
                    dialogPane.getButtonTypes().addAll(sendDirectlyButtonType, cancelButtonType);
                } else {
                    dialogPane.getButtonTypes().addAll(sendDirectlyButtonType, sendCollaborativelyButtonType, cancelButtonType);
                    Button sendCollaborativelyButton = (Button)dialogPane.lookupButton(sendCollaborativelyButtonType);
                    sendCollaborativelyButton.setDisable(true);
                    sendCollaborativelyButton.setDefaultButton(false);
                    payNymController.payNymProperty().addListener((observable, oldValue, payNym) -> {
                        sendCollaborativelyButton.setDisable(payNym == null);
                        sendCollaborativelyButton.setDefaultButton(payNym != null && !payNymController.isLinked(payNym));
                    });
                }

                Button sendDirectlyButton = (Button)dialogPane.lookupButton(sendDirectlyButtonType);
                sendDirectlyButton.setDisable(true);
                sendDirectlyButton.setDefaultButton(true);
                payNymController.payNymProperty().addListener((observable, oldValue, payNym) -> {
                    sendDirectlyButton.setDisable(payNym == null || !payNymController.isLinked(payNym));
                    sendDirectlyButton.setDefaultButton(!sendDirectlyButton.isDisable());
                });
            } else if(operation == Operation.SELECT) {
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

            payNymController.closeProperty().addListener((observable, oldValue, newValue) -> {
                if(newValue) {
                    close();
                }
            });

            setOnCloseRequest(event -> {
                EventManager.get().unregister(payNymController);
            });

            setResultConverter(dialogButton -> {
                if(dialogButton == sendCollaborativelyButtonType) {
                    PayNym payNym = payNymController.getPayNym();
                    payNym.setCollaborativeSend(true);
                    return payNym;
                } else if(dialogButton == sendDirectlyButtonType || dialogButton == selectButtonType) {
                    PayNym payNym = payNymController.getPayNym();
                    payNym.setCollaborativeSend(false);
                    return payNym;
                }

                return null;
            });
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public enum Operation {
        SHOW, SELECT, SEND;
    }
}
