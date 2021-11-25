package com.sparrowwallet.sparrow.soroban;

import com.samourai.soroban.cahoots.CahootsContext;
import com.samourai.soroban.client.cahoots.OnlineCahootsMessage;
import com.samourai.soroban.client.cahoots.SorobanCahootsService;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.cahoots.Cahoots;
import com.samourai.wallet.cahoots.CahootsType;
import com.samourai.wallet.cahoots.stonewallx2.STONEWALLx2;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.ProgressTimer;
import com.sparrowwallet.sparrow.control.TransactionDiagram;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.io.Storage;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;
import static com.sparrowwallet.sparrow.soroban.Soroban.TIMEOUT_MS;

public class InitiatorController extends SorobanController {
    private static final Logger log = LoggerFactory.getLogger(InitiatorController.class);

    private String walletId;
    private Wallet wallet;
    private WalletTransaction walletTransaction;

    @FXML
    private VBox step1;

    @FXML
    private VBox step2;

    @FXML
    private VBox step3;

    @FXML
    private TextField counterparty;

    @FXML
    private ProgressTimer step2Timer;

    @FXML
    private Label step2Desc;

    @FXML
    private ProgressBar sorobanProgressBar;

    @FXML
    private Label sorobanProgressLabel;

    @FXML
    private Glyph mixDeclined;

    @FXML
    private ProgressTimer step3Timer;

    @FXML
    private Label step3Desc;

    @FXML
    private TransactionDiagram transactionDiagram;

    private final ObjectProperty<Step> stepProperty = new SimpleObjectProperty<>(Step.SETUP);

    private final ObjectProperty<Boolean> transactionAccepted = new SimpleObjectProperty<>(null);

    private final ObjectProperty<Transaction> transactionProperty = new SimpleObjectProperty<>(null);

    public void initializeView(String walletId, Wallet wallet, WalletTransaction walletTransaction) {
        this.walletId = walletId;
        this.wallet = wallet;
        this.walletTransaction = walletTransaction;

        step1.managedProperty().bind(step1.visibleProperty());
        step2.managedProperty().bind(step2.visibleProperty());
        step3.managedProperty().bind(step3.visibleProperty());

        sorobanProgressBar.managedProperty().bind(sorobanProgressBar.visibleProperty());
        sorobanProgressLabel.managedProperty().bind(sorobanProgressLabel.visibleProperty());
        mixDeclined.managedProperty().bind(mixDeclined.visibleProperty());
        sorobanProgressBar.visibleProperty().bind(sorobanProgressLabel.visibleProperty());
        mixDeclined.visibleProperty().bind(sorobanProgressLabel.visibleProperty().not());
        step2Timer.visibleProperty().bind(sorobanProgressLabel.visibleProperty());

        step2.setVisible(false);
        step3.setVisible(false);

        transactionAccepted.addListener((observable, oldValue, accepted) -> {
            if(transactionProperty.get() != null) {
                Platform.exitNestedEventLoop(transactionAccepted, accepted);
            }
        });

        transactionProperty.addListener((observable, oldValue, transaction) -> {
            if(transaction != null) {
                updateTransactionDiagram(transactionDiagram, wallet, walletTransaction, transaction);
            }
        });

        step2.visibleProperty().addListener((observable, oldValue, visible) -> {
            if(visible) {
                startInitiatorMeetingRequest();
                step2Timer.start();
            }
        });
    }

    private void startInitiatorMeetingRequest() {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getHdWallet() == null) {
            if(wallet.isEncrypted()) {
                Wallet copy = wallet.copy();
                WalletPasswordDialog dlg = new WalletPasswordDialog(copy.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                Optional<SecureString> password = dlg.showAndWait();
                if(password.isPresent()) {
                    Storage storage = AppServices.get().getOpenWallets().get(wallet);
                    Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(storage, password.get(), true);
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                        ECKey encryptionFullKey = keyDerivationService.getValue();
                        Key key = new Key(encryptionFullKey.getPrivKeyBytes(), storage.getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                        copy.decrypt(key);

                        try {
                            soroban.setHDWallet(copy);
                            startInitiatorMeetingRequest(soroban, wallet);
                        } finally {
                            key.clear();
                            encryptionFullKey.clear();
                            password.get().clear();
                        }
                    });
                    keyDerivationService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                        if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                            Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                                Platform.runLater(this::startInitiatorMeetingRequest);
                            }
                        } else {
                            log.error("Error deriving wallet key", keyDerivationService.getException());
                        }
                    });
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                    keyDerivationService.start();
                } else {
                    step2.setVisible(false);
                    step1.setVisible(true);
                }
            } else {
                soroban.setHDWallet(wallet);
                startInitiatorMeetingRequest(soroban, wallet);
            }
        } else {
            startInitiatorMeetingRequest(soroban, wallet);
        }
    }

    private void startInitiatorMeetingRequest(Soroban soroban, Wallet wallet) {
        SparrowCahootsWallet initiatorCahootsWallet = soroban.getCahootsWallet(wallet, (long)walletTransaction.getFeeRate());
        PaymentCode paymentCodeCounterparty = new PaymentCode(counterparty.getText());

        try {
            SorobanCahootsService sorobanMeetingService = soroban.getSorobanCahootsService(initiatorCahootsWallet);
            sorobanMeetingService.sendMeetingRequest(paymentCodeCounterparty, CahootsType.STONEWALLX2)
                    .subscribeOn(Schedulers.io())
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(meetingRequest -> {
                        sorobanProgressLabel.setText("Waiting for mixing partner...");
                        sorobanMeetingService.receiveMeetingResponse(paymentCodeCounterparty, meetingRequest, TIMEOUT_MS)
                                .subscribeOn(Schedulers.io())
                                .observeOn(JavaFxScheduler.platform())
                                .subscribe(sorobanResponse -> {
                                    if(sorobanResponse.isAccept()) {
                                        sorobanProgressBar.setProgress(0.1);
                                        sorobanProgressLabel.setText("Mixing partner accepted!");
                                        startInitiatorStonewall(initiatorCahootsWallet, paymentCodeCounterparty);
                                    } else {
                                        step2Desc.setText("Mixing partner declined.");
                                        sorobanProgressLabel.setVisible(false);
                                    }
                                }, error -> {
                                    log.error("Error receiving meeting response", error);
                                    String cutFrom = "Exception: ";
                                    int index = error.getMessage().lastIndexOf(cutFrom);
                                    step2Desc.setText(index < 0 ? error.getMessage() : error.getMessage().substring(index + cutFrom.length()));
                                    sorobanProgressLabel.setVisible(false);
                                });
                    }, error -> {
                        log.error("Error sending meeting request", error);
                        step2Desc.setText(error.getMessage());
                        sorobanProgressLabel.setVisible(false);
                    });
        } catch(Exception e) {
            log.error("Error sending meeting request", e);
        }
    }

    private void startInitiatorStonewall(SparrowCahootsWallet initiatorCahootsWallet, PaymentCode paymentCodeCounterparty) {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);

        Payment payment = walletTransaction.getPayments().get(0);
        Map<BlockTransactionHashIndex, WalletNode> firstSetUtxos = walletTransaction.getSelectedUtxoSets().get(0);
        for(Map.Entry<BlockTransactionHashIndex, WalletNode> entry : firstSetUtxos.entrySet()) {
            initiatorCahootsWallet.addUtxo(wallet, entry.getValue(), wallet.getTransactions().get(entry.getKey().getHash()), (int)entry.getKey().getIndex());
        }

        SorobanCahootsService sorobanCahootsService = soroban.getSorobanCahootsService(initiatorCahootsWallet);
        CahootsContext cahootsContext = CahootsContext.newInitiatorStonewallx2(payment.getAmount(), payment.getAddress().toString());

        sorobanCahootsService.getSorobanService().getOnInteraction()
                .observeOn(JavaFxScheduler.platform())
                .subscribe(interaction -> {
            Boolean accepted = (Boolean)Platform.enterNestedEventLoop(transactionAccepted);            
            if(accepted) {
                interaction.sorobanAccept();
            } else {
                interaction.sorobanReject("Mixing partner declined to broadcast the transaction.");
            }
        });

        try {
            sorobanCahootsService.initiator(initiatorCahootsWallet.getAccount(), cahootsContext, paymentCodeCounterparty, TIMEOUT_MS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(sorobanMessage -> {
                                OnlineCahootsMessage cahootsMessage = (OnlineCahootsMessage)sorobanMessage;
                                if(cahootsMessage != null) {
                                    Cahoots cahoots = cahootsMessage.getCahoots();
                                    sorobanProgressBar.setProgress((double)(cahoots.getStep() + 1) / 5);

                                    if(cahoots.getStep() >= 3 && cahoots instanceof STONEWALLx2 stonewallx2) {
                                        try {
                                            Transaction transaction = getTransaction(stonewallx2);
                                            if(transaction != null) {
                                                transactionProperty.set(transaction);
                                                if(cahoots.getStep() == 3) {
                                                    next();
                                                    step3Timer.start(e -> {
                                                        if(stepProperty.get() != Step.BROADCAST) {
                                                            step3Desc.setText("Transaction declined due to timeout.");
                                                            transactionAccepted.set(Boolean.FALSE);
                                                        }
                                                    });
                                                } else if(cahoots.getStep() == 4) {
                                                    stepProperty.set(Step.BROADCAST);
                                                }
                                            }
                                        } catch(PSBTParseException e) {
                                            log.error("Invalid Stonewallx2 PSBT created", e);
                                            step2Desc.setText("Invalid transaction created.");
                                            sorobanProgressLabel.setVisible(false);
                                        }
                                    }
                                }
                            },
                            error -> {
                                log.error("Error creating mix transaction", error);
                                String cutFrom = "Exception: ";
                                int index = error.getMessage().lastIndexOf(cutFrom);
                                step2Desc.setText(index < 0 ? error.getMessage() : error.getMessage().substring(index + cutFrom.length()));
                                sorobanProgressLabel.setVisible(false);
                            });
        } catch(Exception e) {
            log.error("Soroban communication error", e);
        }
    }

    public void next() {
        if(step1.isVisible()) {
            step1.setVisible(false);
            step2.setVisible(true);
            stepProperty.set(Step.COMMUNICATE);
            return;
        }

        if(step2.isVisible()) {
            step2.setVisible(false);
            step3.setVisible(true);
            stepProperty.set(Step.REVIEW);
        }
    }

    public void accept() {
        transactionAccepted.set(Boolean.TRUE);
    }

    public void cancel() {
        transactionAccepted.set(Boolean.FALSE);
    }

    public ObjectProperty<Step> stepProperty() {
        return stepProperty;
    }

    public Transaction getTransaction() {
        return transactionProperty.get();
    }

    public ObjectProperty<Boolean> transactionAcceptedProperty() {
        return transactionAccepted;
    }

    public enum Step {
        SETUP, COMMUNICATE, REVIEW, BROADCAST
    }
}
