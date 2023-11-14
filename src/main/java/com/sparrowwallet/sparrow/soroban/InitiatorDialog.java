package com.sparrowwallet.sparrow.soroban;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class InitiatorDialog extends Dialog<Transaction> {
    private static final Logger log = LoggerFactory.getLogger(InitiatorDialog.class);

    private final boolean confirmationRequired;

    public InitiatorDialog(String walletId, Wallet wallet, WalletTransaction walletTransaction) {
        this.confirmationRequired = AppServices.getSorobanServices().getSoroban(walletId).getHdWallet() != null;

        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.onEscapePressed(dialogPane.getScene(), this::close);

        try {
            FXMLLoader initiatorLoader = new FXMLLoader(AppServices.class.getResource("soroban/initiator.fxml"));
            dialogPane.setContent(initiatorLoader.load());
            InitiatorController initiatorController = initiatorLoader.getController();
            initiatorController.initializeView(walletId, wallet, walletTransaction);

            EventManager.get().register(initiatorController);

            dialogPane.setPrefWidth(730);
            dialogPane.setPrefHeight(530);
            dialogPane.setMinHeight(dialogPane.getPrefHeight());
            AppServices.moveToActiveWindowScreen(this);

            dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
            dialogPane.getStylesheets().add(AppServices.class.getResource("app.css").toExternalForm());
            dialogPane.getStylesheets().add(AppServices.class.getResource("soroban/initiator.css").toExternalForm());

            final ButtonType nextButtonType = new javafx.scene.control.ButtonType("Next", ButtonBar.ButtonData.OK_DONE);
            final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            final ButtonType broadcastButtonType = new javafx.scene.control.ButtonType("Sign & Broadcast", ButtonBar.ButtonData.APPLY);
            final ButtonType doneButtonType = new javafx.scene.control.ButtonType("Done", ButtonBar.ButtonData.APPLY);
            dialogPane.getButtonTypes().addAll(nextButtonType, cancelButtonType, broadcastButtonType, doneButtonType);

            Button nextButton = (Button)dialogPane.lookupButton(nextButtonType);
            Button cancelButton = (Button)dialogPane.lookupButton(cancelButtonType);
            Button broadcastButton = (Button)dialogPane.lookupButton(broadcastButtonType);
            Button doneButton = (Button)dialogPane.lookupButton(doneButtonType);
            nextButton.setDisable(initiatorController.counterpartyPaymentCodeProperty().get() == null);
            broadcastButton.setDisable(true);

            nextButton.managedProperty().bind(nextButton.visibleProperty());
            cancelButton.managedProperty().bind(cancelButton.visibleProperty());
            broadcastButton.managedProperty().bind(broadcastButton.visibleProperty());
            doneButton.managedProperty().bind(doneButton.visibleProperty());

            broadcastButton.setVisible(false);
            doneButton.setVisible(false);

            initiatorController.counterpartyPaymentCodeProperty().addListener((observable, oldValue, paymentCode) -> {
                nextButton.setDisable(paymentCode == null || !AppServices.isConnected());
            });

            initiatorController.stepProperty().addListener((observable, oldValue, step) -> {
                if(step == InitiatorController.Step.SETUP) {
                    nextButton.setDisable(false);
                    nextButton.setVisible(true);
                } else if(step == InitiatorController.Step.COMMUNICATE) {
                    nextButton.setDisable(true);
                    nextButton.setVisible(true);
                } else if(step == InitiatorController.Step.REVIEW) {
                    nextButton.setVisible(false);
                    broadcastButton.setVisible(true);
                    broadcastButton.setDefaultButton(true);
                    broadcastButton.setDisable(false);
                } else if(step == InitiatorController.Step.BROADCAST) {
                    cancelButton.setVisible(false);
                    broadcastButton.setVisible(false);
                    doneButton.setVisible(true);
                    doneButton.setDefaultButton(true);
                } else if(step == InitiatorController.Step.REBROADCAST) {
                    cancelButton.setVisible(true);
                    broadcastButton.setVisible(true);
                    broadcastButton.setDisable(false);
                    doneButton.setVisible(false);
                }
            });

            initiatorController.transactionAcceptedProperty().addListener((observable, oldValue, accepted) -> {
                broadcastButton.setDisable(accepted != Boolean.TRUE);
            });

            nextButton.addEventFilter(ActionEvent.ACTION, event -> {
                initiatorController.next();
                event.consume();
            });

            cancelButton.addEventFilter(ActionEvent.ACTION, event -> {
                initiatorController.cancel();
            });

            broadcastButton.addEventFilter(ActionEvent.ACTION, event -> {
                if(initiatorController.isTransactionAccepted()) {
                    initiatorController.broadcastTransaction();
                } else {
                    acceptAndBroadcast(initiatorController, walletId, wallet);
                }
                event.consume();
            });

            setOnCloseRequest(event -> {
                initiatorController.close();
                EventManager.get().unregister(initiatorController);
            });

            setResultConverter(dialogButton -> dialogButton.getButtonData().equals(ButtonBar.ButtonData.APPLY) ? initiatorController.getTransaction() : null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void acceptAndBroadcast(InitiatorController initiatorController, String walletId, Wallet wallet) {
        if(confirmationRequired && wallet.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            dlg.initOwner(getDialogPane().getScene().getWindow());
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage storage = AppServices.get().getOpenWallets().get(wallet);
                Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(storage, password.get(), true);
                keyDerivationService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                    initiatorController.accept();
                    password.get().clear();
                });
                keyDerivationService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                    if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                        Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                        if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                            Platform.runLater(() -> acceptAndBroadcast(initiatorController, walletId, wallet));
                        }
                    } else {
                        log.error("Error deriving wallet key", keyDerivationService.getException());
                    }
                });
                EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                keyDerivationService.start();
            }
        } else {
            initiatorController.accept();
        }
    }
}
