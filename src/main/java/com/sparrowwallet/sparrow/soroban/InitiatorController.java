package com.sparrowwallet.sparrow.soroban;

import com.google.common.eventbus.Subscribe;
import com.samourai.soroban.cahoots.CahootsContext;
import com.samourai.soroban.client.cahoots.OnlineCahootsMessage;
import com.samourai.soroban.client.cahoots.SorobanCahootsService;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.cahoots.Cahoots;
import com.samourai.wallet.cahoots.CahootsType;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.PayNymAvatar;
import com.sparrowwallet.sparrow.control.ProgressTimer;
import com.sparrowwallet.sparrow.control.TransactionDiagram;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.event.WalletNodeHistoryChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import io.reactivex.Observable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.UnaryOperator;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;
import static com.sparrowwallet.sparrow.soroban.Soroban.TIMEOUT_MS;

public class InitiatorController extends SorobanController {
    private static final Logger log = LoggerFactory.getLogger(InitiatorController.class);

    private static final PayNym FIND_FOLLOWERS = new PayNym(null, null, "Retrieve Contacts...", false, Collections.emptyList(), Collections.emptyList());

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
    private VBox step4;

    @FXML
    private ComboBox<PayNym> payNymFollowers;

    @FXML
    private TextField counterparty;

    @FXML
    private ProgressIndicator payNymLoading;

    @FXML
    private Button findPayNym;

    @FXML
    private PayNymAvatar payNymAvatar;

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

    @FXML
    private Label step4Desc;

    @FXML
    private ProgressBar broadcastProgressBar;

    @FXML
    private Label broadcastProgressLabel;

    @FXML
    private Glyph broadcastSuccessful;

    private final StringProperty counterpartyPayNymName = new SimpleStringProperty();

    private final ObjectProperty<PaymentCode> counterpartyPaymentCode = new SimpleObjectProperty<>(null);

    private final ObjectProperty<Step> stepProperty = new SimpleObjectProperty<>(Step.SETUP);

    private final ObjectProperty<Boolean> transactionAccepted = new SimpleObjectProperty<>(null);

    private final ObjectProperty<Transaction> transactionProperty = new SimpleObjectProperty<>(null);

    private CahootsType cahootsType = CahootsType.STONEWALLX2;

    private ElectrumServer.TransactionMempoolService transactionMempoolService;

    private boolean closed;

    public void initializeView(String walletId, Wallet wallet, WalletTransaction walletTransaction) {
        this.walletId = walletId;
        this.wallet = wallet;
        this.walletTransaction = walletTransaction;

        step1.managedProperty().bind(step1.visibleProperty());
        step2.managedProperty().bind(step2.visibleProperty());
        step3.managedProperty().bind(step3.visibleProperty());
        step4.managedProperty().bind(step4.visibleProperty());

        sorobanProgressBar.managedProperty().bind(sorobanProgressBar.visibleProperty());
        sorobanProgressLabel.managedProperty().bind(sorobanProgressLabel.visibleProperty());
        mixDeclined.managedProperty().bind(mixDeclined.visibleProperty());
        sorobanProgressBar.visibleProperty().bind(sorobanProgressLabel.visibleProperty());
        mixDeclined.visibleProperty().bind(sorobanProgressLabel.visibleProperty().not());
        step2Timer.visibleProperty().bind(sorobanProgressLabel.visibleProperty());
        broadcastProgressBar.managedProperty().bind(broadcastProgressBar.visibleProperty());
        broadcastProgressLabel.managedProperty().bind(broadcastProgressLabel.visibleProperty());
        broadcastSuccessful.managedProperty().bind(broadcastSuccessful.visibleProperty());
        broadcastSuccessful.setVisible(false);

        step2.setVisible(false);
        step3.setVisible(false);
        step4.setVisible(false);

        transactionAccepted.addListener((observable, oldValue, accepted) -> {
            if(transactionProperty.get() != null && stepProperty.get() != Step.REBROADCAST) {
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

        payNymLoading.managedProperty().bind(payNymLoading.visibleProperty());
        payNymLoading.maxHeightProperty().bind(counterparty.heightProperty());
        payNymLoading.setVisible(false);

        payNymAvatar.managedProperty().bind(payNymAvatar.visibleProperty());
        payNymFollowers.prefWidthProperty().bind(counterparty.widthProperty());
        payNymFollowers.valueProperty().addListener((observable, oldValue, payNym) -> {
            if(payNym == FIND_FOLLOWERS) {
                Config.get().setUsePayNym(true);
                setPayNymFollowers();
            } else if(payNym != null) {
                counterpartyPayNymName.set(payNym.nymName());
                counterpartyPaymentCode.set(payNym.paymentCode());
                payNymAvatar.setPaymentCode(payNym.paymentCode());
                counterparty.setText(payNym.nymName());
                step1.requestFocus();
            }
        });
        payNymFollowers.setConverter(new StringConverter<>() {
            @Override
            public String toString(PayNym payNym) {
                return payNym == null ? "" : payNym.nymName();
            }

            @Override
            public PayNym fromString(String string) {
                return null;
            }
        });

        UnaryOperator<TextFormatter.Change> paymentCodeFilter = change -> {
            String input = change.getControlNewText();
            if(input.startsWith("P") && !input.contains("...")) {
                try {
                    PaymentCode paymentCode = new PaymentCode(input);
                    if(paymentCode.isValid()) {
                        counterpartyPaymentCode.set(paymentCode);
                        if(payNymAvatar.getPaymentCode() == null || !input.equals(payNymAvatar.getPaymentCode().toString())) {
                            payNymAvatar.setPaymentCode(paymentCode);
                        }

                        TextInputControl control = (TextInputControl)change.getControl();
                        change.setText(input.substring(0, 12) + "..." + input.substring(input.length() - 5));
                        change.setRange(0, control.getLength());
                        change.setAnchor(change.getText().length());
                        change.setCaretPosition(change.getText().length());
                    }
                } catch(Exception e) {
                    //ignore
                }
            }

            return change;
        };
        counterparty.setTextFormatter(new TextFormatter<>(paymentCodeFilter));

        counterparty.textProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                if(newValue.startsWith("P") && newValue.contains("...") && newValue.length() == 20 && counterpartyPaymentCode.get() != null) {
                    //Assumed valid payment code
                } else if(Config.get().isUsePayNym() && PAYNYM_REGEX.matcher(newValue).matches()) {
                    if(!newValue.equals(counterpartyPayNymName.get())) {
                        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
                        payNymLoading.setVisible(true);
                        soroban.getPayNym(newValue).subscribe(payNym -> {
                            payNymLoading.setVisible(false);
                            counterpartyPayNymName.set(payNym.nymName());
                            counterpartyPaymentCode.set(payNym.paymentCode());
                            payNymAvatar.setPaymentCode(payNym.paymentCode());
                        }, error -> {
                            payNymLoading.setVisible(false);
                            //ignore, probably doesn't exist but will try again on meeting request
                        });
                    }
                } else {
                    counterpartyPayNymName.set(null);
                    counterpartyPaymentCode.set(null);
                    payNymAvatar.setPaymentCode(null);
                }
            }
        });

        stepProperty.addListener((observable, oldValue, step) -> {
            if(step == Step.BROADCAST) {
                step4Desc.setText("Broadcasting the mix transaction...");
                broadcastProgressLabel.setVisible(true);
            } else if(step == Step.REBROADCAST) {
                step4Desc.setText("Rebroadcast the mix transaction.");
                broadcastProgressLabel.setVisible(false);
            }
        });

        Payment payment = walletTransaction.getPayments().get(0);
        if(payment.getAddress() instanceof PayNymAddress payNymAddress) {
            PayNym payNym = payNymAddress.getPayNym();
            counterpartyPayNymName.set(payNym.nymName());
            counterpartyPaymentCode.set(payNym.paymentCode());
            payNymAvatar.setPaymentCode(payNym.paymentCode());
            counterparty.setText(payNym.nymName());
            counterparty.setEditable(false);
            findPayNym.setVisible(false);
            cahootsType = CahootsType.STOWAWAY;
        } else if(Config.get().isUsePayNym()) {
            setPayNymFollowers();
        } else {
            List<PayNym> defaultList = new ArrayList<>();
            defaultList.add(FIND_FOLLOWERS);
            payNymFollowers.setItems(FXCollections.observableList(defaultList));
        }

        ValidationSupport validationSupport = new ValidationSupport();
        Platform.runLater(() -> {
            validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
            validationSupport.registerValidator(counterparty, (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Invalid counterparty", !isValidCounterparty()));
        });
    }

    private void setPayNymFollowers() {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getPaymentCode() != null) {
            soroban.getFollowing().subscribe(followerPayNyms -> {
                findPayNym.setVisible(true);
                payNymFollowers.setItems(FXCollections.observableList(followerPayNyms));
            }, error -> {
                if(error.getMessage().endsWith("404")) {
                    Config.get().setUsePayNym(false);
                    AppServices.showErrorDialog("Could not retrieve PayNym", "This wallet does not have an associated PayNym or any followers yet. You can retrieve the PayNym using the Find PayNym button.");
                } else {
                    log.warn("Could not retrieve followers: ", error);
                }
            });
        }
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

        getPaymentCodeCounterparty(soroban).subscribe(paymentCodeCounterparty -> {
            try {
                SorobanCahootsService sorobanMeetingService = soroban.getSorobanCahootsService(initiatorCahootsWallet);
                sorobanMeetingService.sendMeetingRequest(paymentCodeCounterparty, cahootsType)
                        .subscribeOn(Schedulers.io())
                        .observeOn(JavaFxScheduler.platform())
                        .subscribe(meetingRequest -> {
                            sorobanProgressLabel.setText("Waiting for mix partner...");
                            sorobanMeetingService.receiveMeetingResponse(paymentCodeCounterparty, meetingRequest, TIMEOUT_MS)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(JavaFxScheduler.platform())
                                    .subscribe(sorobanResponse -> {
                                        if(sorobanResponse.isAccept()) {
                                            sorobanProgressBar.setProgress(0.1);
                                            sorobanProgressLabel.setText("Mix partner accepted!");
                                            startInitiatorCollaborative(initiatorCahootsWallet, paymentCodeCounterparty);
                                        } else {
                                            step2Desc.setText("Mix partner declined.");
                                            sorobanProgressLabel.setVisible(false);
                                        }
                                    }, error -> {
                                        log.error("Error receiving meeting response", error);
                                        String cutFrom = "Exception: ";
                                        int index = error.getMessage().lastIndexOf(cutFrom);
                                        String msg = index < 0 ? error.getMessage() : error.getMessage().substring(index + cutFrom.length());
                                        msg = msg.replace("#Cahoots", "mix transaction");
                                        step2Desc.setText(msg);
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
        }, error -> {
            log.error("Could not retrieve payment code", error);
            if(error.getMessage().endsWith("404")) {
                step2Desc.setText("PayNym not found");
            } else if(error.getMessage().endsWith("400")) {
                step2Desc.setText("Could not retrieve PayNym");
            } else {
                step2Desc.setText(error.getMessage());
            }
            sorobanProgressLabel.setVisible(false);
        });
    }

    private void startInitiatorCollaborative(SparrowCahootsWallet initiatorCahootsWallet, PaymentCode paymentCodeCounterparty) {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);

        Payment payment = walletTransaction.getPayments().get(0);
        Map<BlockTransactionHashIndex, WalletNode> firstSetUtxos = walletTransaction.isCoinControlUsed() ? walletTransaction.getSelectedUtxoSets().get(0) : wallet.getWalletUtxos();
        for(Map.Entry<BlockTransactionHashIndex, WalletNode> entry : firstSetUtxos.entrySet()) {
            initiatorCahootsWallet.addUtxo(wallet, entry.getValue(), wallet.getTransactions().get(entry.getKey().getHash()), (int)entry.getKey().getIndex());
        }

        SorobanCahootsService sorobanCahootsService = soroban.getSorobanCahootsService(initiatorCahootsWallet);
        CahootsContext cahootsContext = cahootsType == CahootsType.STONEWALLX2 ?
                CahootsContext.newInitiatorStonewallx2(payment.getAmount(), payment.getAddress().toString()) :
                CahootsContext.newInitiatorStowaway(payment.getAmount());

        sorobanCahootsService.getSorobanService().getOnInteraction()
                .observeOn(JavaFxScheduler.platform())
                .subscribe(interaction -> {
            Boolean accepted = (Boolean)Platform.enterNestedEventLoop(transactionAccepted);            
            if(accepted) {
                interaction.sorobanAccept();
            } else {
                interaction.sorobanReject("Mix partner declined to broadcast the transaction.");
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

                                    if(cahoots.getStep() >= 3) {
                                        try {
                                            Transaction transaction = getTransaction(cahoots);
                                            if(transaction != null) {
                                                transactionProperty.set(transaction);
                                                if(cahoots.getStep() == 3) {
                                                    next();
                                                    step3Timer.start(e -> {
                                                        if(stepProperty.get() != Step.BROADCAST && stepProperty.get() != Step.REBROADCAST) {
                                                            step3Desc.setText("Transaction declined due to timeout.");
                                                            transactionAccepted.set(Boolean.FALSE);
                                                        }
                                                    });
                                                } else if(cahoots.getStep() == 4) {
                                                    next();
                                                    broadcastTransaction();
                                                }
                                            }
                                        } catch(PSBTParseException e) {
                                            log.error("Invalid collaborative PSBT created", e);
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

    public void broadcastTransaction() {
        stepProperty.set(Step.BROADCAST);

        ElectrumServer.BroadcastTransactionService broadcastTransactionService = new ElectrumServer.BroadcastTransactionService(getTransaction());
        broadcastTransactionService.setOnRunning(workerStateEvent -> {
            broadcastProgressBar.setProgress(-1);
        });
        broadcastTransactionService.setOnSucceeded(workerStateEvent -> {
            Map<BlockTransactionHashIndex, WalletNode> selectedUtxos = new HashMap<>();
            Map<BlockTransactionHashIndex, WalletNode> walletTxos = wallet.getWalletTxos();
            for(TransactionInput txInput : getTransaction().getInputs()) {
                Optional<BlockTransactionHashIndex> optSelectedUtxo = walletTxos.keySet().stream().filter(txo -> txInput.getOutpoint().getHash().equals(txo.getHash()) && txInput.getOutpoint().getIndex() == txo.getIndex())
                        .findFirst();
                optSelectedUtxo.ifPresent(blockTransactionHashIndex -> selectedUtxos.put(blockTransactionHashIndex, walletTxos.get(blockTransactionHashIndex)));
            }

            if(transactionMempoolService != null) {
                transactionMempoolService.cancel();
            }

            transactionMempoolService = new ElectrumServer.TransactionMempoolService(wallet, getTransaction().getTxId(), new HashSet<>(selectedUtxos.values()));
            transactionMempoolService.setDelay(Duration.seconds(3));
            transactionMempoolService.setPeriod(Duration.seconds(10));
            transactionMempoolService.setRestartOnFailure(false);
            transactionMempoolService.setOnSucceeded(mempoolWorkerStateEvent -> {
                Set<String> scriptHashes = transactionMempoolService.getValue();
                if(!scriptHashes.isEmpty()) {
                    Platform.runLater(() -> EventManager.get().post(new WalletNodeHistoryChangedEvent(scriptHashes.iterator().next())));
                }

                if(transactionMempoolService.getIterationCount() > 3 && transactionMempoolService.isRunning()) {
                    transactionMempoolService.cancel();
                    broadcastProgressBar.setProgress(0);
                    log.error("Timeout searching for broadcasted transaction");
                    AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not register it in the mempool. It is safe to try broadcasting again.");
                    stepProperty.set(Step.REBROADCAST);
                }
            });
            transactionMempoolService.setOnFailed(mempoolWorkerStateEvent -> {
                transactionMempoolService.cancel();
                broadcastProgressBar.setProgress(0);
                log.error("Timeout searching for broadcasted transaction");
                AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not indicate it had entered the mempool. It is safe to try broadcasting again.");
                stepProperty.set(Step.REBROADCAST);
            });

            if(!closed) {
                transactionMempoolService.start();
            }
        });
        broadcastTransactionService.setOnFailed(workerStateEvent -> {
            broadcastProgressBar.setProgress(0);
            Throwable exception = workerStateEvent.getSource().getException();
            while(exception.getCause() != null) {
                exception = exception.getCause();
            }

            log.error("Error broadcasting transaction", exception);
            AppServices.showErrorDialog("Error broadcasting transaction", exception.getMessage());
            stepProperty.set(Step.REBROADCAST);
        });
        broadcastTransactionService.start();
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
            return;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step4.setVisible(true);
            stepProperty.set(Step.BROADCAST);
        }
    }

    private Observable<PaymentCode> getPaymentCodeCounterparty(Soroban soroban) {
        if(counterpartyPaymentCode.get() != null) {
            return Observable.just(counterpartyPaymentCode.get());
        } else {
            return soroban.getPayNym(counterparty.getText()).map(PayNym::paymentCode);
        }
    }

    private boolean isValidCounterparty() {
        if(counterpartyPaymentCode.get() != null) {
            return true;
        }

        if(counterparty.getText().startsWith("P") && counterparty.getText().contains("...") && counterparty.getText().length() == 20) {
            return true;
        }

        return PAYNYM_REGEX.matcher(counterparty.getText()).matches();
    }

    public void accept() {
        transactionAccepted.set(Boolean.TRUE);
    }

    public void cancel() {
        transactionAccepted.set(Boolean.FALSE);
    }

    public void findPayNym(ActionEvent event) {
        PayNymDialog payNymDialog = new PayNymDialog(walletId, true);
        Optional<PayNym> optPayNym = payNymDialog.showAndWait();
        optPayNym.ifPresent(payNym -> {
            counterpartyPayNymName.set(payNym.nymName());
            counterpartyPaymentCode.set(payNym.paymentCode());
            payNymAvatar.setPaymentCode(payNym.paymentCode());
            counterparty.setText(payNym.nymName());
            step1.requestFocus();
        });
    }

    public ObjectProperty<PaymentCode> counterpartyPaymentCodeProperty() {
        return counterpartyPaymentCode;
    }

    public ObjectProperty<Step> stepProperty() {
        return stepProperty;
    }

    public Transaction getTransaction() {
        return transactionProperty.get();
    }

    public void close() {
        closed = true;
        if(transactionMempoolService != null) {
            transactionMempoolService.cancel();
        }
    }

    public boolean isTransactionAccepted() {
        return transactionAccepted.get() == Boolean.TRUE;
    }

    public ObjectProperty<Boolean> transactionAcceptedProperty() {
        return transactionAccepted;
    }

    @Subscribe
    public void walletNodeHistoryChanged(WalletNodeHistoryChangedEvent event) {
        if(event.getWalletNode(wallet) != null) {
            if(transactionMempoolService != null) {
                transactionMempoolService.cancel();
            }

            broadcastProgressBar.setVisible(false);
            broadcastProgressLabel.setVisible(false);
            step4Desc.setText("Transaction broadcasted.");
            broadcastSuccessful.setVisible(true);
        }
    }

    public enum Step {
        SETUP, COMMUNICATE, REVIEW, BROADCAST, REBROADCAST
    }
}
