package com.sparrowwallet.sparrow.soroban;

import com.samourai.soroban.cahoots.CahootsContext;
import com.samourai.soroban.client.cahoots.OnlineCahootsMessage;
import com.samourai.soroban.client.cahoots.SorobanCahootsService;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.cahoots.Cahoots;
import com.samourai.wallet.cahoots.CahootsType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.io.Config;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.sparrowwallet.sparrow.soroban.Soroban.TIMEOUT_MS;

public class CounterpartyController extends SorobanController {
    private static final Logger log = LoggerFactory.getLogger(CounterpartyController.class);

    private String walletId;
    private Wallet wallet;

    @FXML
    private VBox step1;

    @FXML
    private VBox step2;

    @FXML
    private VBox step3;

    @FXML
    private VBox step4;

    @FXML
    private CopyableTextField payNym;

    @FXML
    private PayNymAvatar payNymAvatar;

    @FXML
    private Button payNymButton;

    @FXML
    private PaymentCodeTextField paymentCode;

    @FXML
    private Button paymentCodeQR;

    @FXML
    private ComboBox<Wallet> mixWallet;

    @FXML
    private ProgressTimer step2Timer;

    @FXML
    private Label step2Desc;

    @FXML
    private Label mixingPartner;

    @FXML
    private PayNymAvatar mixPartnerAvatar;

    @FXML
    private Label meetingFail;

    @FXML
    private VBox mixDetails;

    @FXML
    private Label mixType;

    @FXML
    private ProgressTimer step3Timer;

    @FXML
    private Label step3Desc;

    @FXML
    private ProgressBar sorobanProgressBar;

    @FXML
    private Label sorobanProgressLabel;

    @FXML
    private Glyph mixDeclined;

    @FXML
    private TransactionDiagram transactionDiagram;

    private final ObjectProperty<Boolean> meetingReceived = new SimpleObjectProperty<>(null);

    private final ObjectProperty<Boolean> meetingAccepted = new SimpleObjectProperty<>(null);

    private final ObjectProperty<Transaction> transactionProperty = new SimpleObjectProperty<>(null);

    public void initializeView(String walletId, Wallet wallet) {
        this.walletId = walletId;
        this.wallet = wallet;

        step1.managedProperty().bind(step1.visibleProperty());
        step2.managedProperty().bind(step2.visibleProperty());
        step3.managedProperty().bind(step3.visibleProperty());
        step4.managedProperty().bind(step4.visibleProperty());

        mixWallet.setConverter(new StringConverter<>() {
            @Override
            public String toString(Wallet wallet) {
                return wallet == null ? "" : wallet.getFullDisplayName();
            }

            @Override
            public Wallet fromString(String string) {
                return null;
            }
        });
        mixWallet.setItems(FXCollections.observableList(wallet.getAllWallets()));
        mixWallet.setValue(wallet);
        mixWallet.valueProperty().addListener((observable, oldValue, selectedWallet) -> setWallet(selectedWallet));

        sorobanProgressBar.managedProperty().bind(sorobanProgressBar.visibleProperty());
        sorobanProgressLabel.managedProperty().bind(sorobanProgressLabel.visibleProperty());
        mixDeclined.managedProperty().bind(mixDeclined.visibleProperty());
        sorobanProgressBar.visibleProperty().bind(sorobanProgressLabel.visibleProperty());
        mixDeclined.visibleProperty().bind(sorobanProgressLabel.visibleProperty().not());
        step2Timer.visibleProperty().bind(mixingPartner.visibleProperty());
        step3Timer.visibleProperty().bind(sorobanProgressLabel.visibleProperty());

        step2.setVisible(false);
        step3.setVisible(false);
        step4.setVisible(false);

        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getHdWallet() == null) {
            throw new IllegalStateException("Soroban HD wallet must be set");
        }

        payNym.managedProperty().bind(payNym.visibleProperty());
        payNymAvatar.managedProperty().bind(payNymAvatar.visibleProperty());
        payNymAvatar.visibleProperty().bind(payNym.visibleProperty());
        payNymButton.managedProperty().bind(payNymButton.visibleProperty());
        payNymButton.visibleProperty().bind(payNym.visibleProperty().not());
        if(Config.get().isUsePayNym()) {
            retrievePayNym(null);
        } else {
            payNym.setVisible(false);
        }

        paymentCode.setPaymentCode(soroban.getPaymentCode());
        paymentCodeQR.prefHeightProperty().bind(paymentCode.heightProperty());

        mixingPartner.managedProperty().bind(mixingPartner.visibleProperty());
        meetingFail.managedProperty().bind(meetingFail.visibleProperty());
        meetingFail.visibleProperty().bind(mixingPartner.visibleProperty().not());

        mixDetails.managedProperty().bind(mixDetails.visibleProperty());
        mixDetails.setVisible(false);

        meetingAccepted.addListener((observable, oldValue, accepted) -> {
            Platform.exitNestedEventLoop(meetingAccepted, accepted);
            meetingReceived.set(null);
        });

        step2.visibleProperty().addListener((observable, oldValue, visible) -> {
            if(visible) {
                startCounterpartyMeetingReceive();
                step2Timer.start(e -> {
                    step2Desc.setText("Mix declined due to timeout.");
                    meetingReceived.set(Boolean.FALSE);
                });
            }
        });

        step3.visibleProperty().addListener((observable, oldValue, visible) -> {
            if(visible) {
                meetingAccepted.set(Boolean.TRUE);
            }
        });
    }

    private void setWallet(Wallet wallet) {
        this.walletId = AppServices.get().getOpenWallets().get(wallet).getWalletId(wallet);
        this.wallet = wallet;
    }

    private void startCounterpartyMeetingReceive() {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        SparrowCahootsWallet counterpartyCahootsWallet = soroban.getCahootsWallet(wallet, 1);

        try {
            SorobanCahootsService sorobanMeetingService = soroban.getSorobanCahootsService(counterpartyCahootsWallet);
            sorobanMeetingService.receiveMeetingRequest(TIMEOUT_MS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(requestMessage -> {
                        String code = requestMessage.getSender();
                        CahootsType cahootsType = requestMessage.getType();
                        PaymentCode paymentCodeInitiator = new PaymentCode(code);
                        updateMixPartner(soroban, paymentCodeInitiator, cahootsType);
                        Boolean accepted = (Boolean)Platform.enterNestedEventLoop(meetingAccepted);
                        sorobanMeetingService.sendMeetingResponse(paymentCodeInitiator, requestMessage, accepted)
                                .subscribeOn(Schedulers.io())
                                .observeOn(JavaFxScheduler.platform())
                                .subscribe(responseMessage -> {
                                    if(accepted) {
                                        startCounterpartyCollaboration(counterpartyCahootsWallet, paymentCodeInitiator, cahootsType);
                                        followPaymentCode(soroban, paymentCodeInitiator);
                                    }
                                }, error -> {
                                    log.error("Error sending meeting response", error);
                                    mixingPartner.setVisible(false);
                                });
                    }, error -> {
                        log.error("Failed to receive meeting request", error);
                        mixingPartner.setVisible(false);
                    });
        } catch(Exception e) {
            log.error("Error sending meeting response", e);
        }
    }

    private void updateMixPartner(Soroban soroban, PaymentCode paymentCodeInitiator, CahootsType cahootsType) {
        String code = paymentCodeInitiator.toString();
        mixingPartner.setText(code.substring(0, 12) + "..." + code.substring(code.length() - 5));
        if(Config.get().isUsePayNym()) {
            mixPartnerAvatar.setPaymentCode(paymentCodeInitiator);
            soroban.getPayNym(paymentCodeInitiator.toString()).subscribe(payNym -> {
                mixingPartner.setText(payNym.nymName());
            }, error -> {
                //ignore, may not be a PayNym
            });
        }

        mixType.setText(cahootsType.getLabel());
        mixDetails.setVisible(true);
        meetingReceived.set(Boolean.TRUE);
    }

    private void startCounterpartyCollaboration(SparrowCahootsWallet counterpartyCahootsWallet, PaymentCode initiatorPaymentCode, CahootsType cahootsType) {
        sorobanProgressLabel.setText("Creating mix transaction...");

        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        Map<BlockTransactionHashIndex, WalletNode> walletUtxos = wallet.getWalletUtxos();
        for(Map.Entry<BlockTransactionHashIndex, WalletNode> entry : walletUtxos.entrySet()) {
            counterpartyCahootsWallet.addUtxo(wallet, entry.getValue(), wallet.getTransactions().get(entry.getKey().getHash()), (int)entry.getKey().getIndex());
        }

        try {
            SorobanCahootsService sorobanCahootsService = soroban.getSorobanCahootsService(counterpartyCahootsWallet);
            CahootsContext cahootsContext = cahootsType == CahootsType.STONEWALLX2 ? CahootsContext.newCounterpartyStonewallx2() : CahootsContext.newCounterpartyStowaway();
            sorobanCahootsService.contributor(counterpartyCahootsWallet.getAccount(), cahootsContext, initiatorPaymentCode, TIMEOUT_MS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(sorobanMessage -> {
                                OnlineCahootsMessage cahootsMessage = (OnlineCahootsMessage)sorobanMessage;
                                if(cahootsMessage != null) {
                                    Cahoots cahoots = cahootsMessage.getCahoots();
                                    sorobanProgressBar.setProgress((double)(cahoots.getStep() + 1) / 5);

                                    if(cahoots.getStep() == 3) {
                                        sorobanProgressLabel.setText("Your mix partner is reviewing the transaction...");
                                        step3Timer.start();
                                    } else if(cahoots.getStep() >= 4) {
                                        try {
                                            Transaction transaction = getTransaction(cahoots);
                                            if(transaction != null) {
                                                transactionProperty.set(transaction);
                                                updateTransactionDiagram(transactionDiagram, wallet, null, transaction);
                                                next();
                                            }
                                        } catch(PSBTParseException e) {
                                            log.error("Invalid collaborative PSBT created", e);
                                            step3Desc.setText("Invalid transaction created.");
                                            sorobanProgressLabel.setVisible(false);
                                        }
                                    }
                                }
                            }, error -> {
                                log.error("Error creating mix transaction", error);
                                String cutFrom = "Exception: ";
                                int index = error.getMessage().lastIndexOf(cutFrom);
                                String msg = index < 0 ? error.getMessage() : error.getMessage().substring(index + cutFrom.length());
                                msg = msg.replace("#Cahoots", "mix transaction");
                                step3Desc.setText(msg);
                                sorobanProgressLabel.setVisible(false);
                            });
        } catch(Exception e) {
            log.error("Error creating mix transaction", e);
            sorobanProgressLabel.setText(e.getMessage());
        }
    }

    private void followPaymentCode(Soroban soroban, PaymentCode paymentCodeInitiator) {
        if(Config.get().isUsePayNym()) {
            soroban.getAuthToken(new HashMap<>()).subscribe(authToken -> {
                String signature = soroban.getSignature(authToken);
                soroban.followPaymentCode(paymentCodeInitiator, authToken, signature).subscribe(followMap -> {
                   log.debug("Followed payment code " + followMap.get("following"));
                }, error -> {
                    log.warn("Could not follow payment code", error);
                });
            });
        }
    }

    public boolean next() {
        if(step1.isVisible()) {
            step1.setVisible(false);
            step2.setVisible(true);
            return true;
        }

        if(step2.isVisible()) {
            step2.setVisible(false);
            step3.setVisible(true);
            return true;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step4.setVisible(true);
            return true;
        }

        return false;
    }

    public void cancel() {
        meetingAccepted.set(Boolean.FALSE);
    }

    public void retrievePayNym(ActionEvent event) {
        Config.get().setUsePayNym(true);

        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        soroban.createPayNym().subscribe(createMap -> {
            payNym.setText((String)createMap.get("nymName"));
            payNymAvatar.setPaymentCode(soroban.getPaymentCode());
            payNym.setVisible(true);

            if(createMap.get("claimed") == Boolean.FALSE) {
                soroban.getAuthToken(createMap).subscribe(authToken -> {
                    String signature = soroban.getSignature(authToken);
                    soroban.claimPayNym(authToken, signature).subscribe(claimMap -> {
                        log.debug("Claimed payment code " + claimMap.get("claimed"));
                        soroban.addSamouraiPaymentCode(authToken, signature).subscribe(addMap -> {
                            log.debug("Added payment code " + addMap);
                        });
                    }, error -> {
                        soroban.getAuthToken(new HashMap<>()).subscribe(newAuthToken -> {
                            String newSignature = soroban.getSignature(newAuthToken);
                            soroban.claimPayNym(newAuthToken, newSignature).subscribe(claimMap -> {
                                log.debug("Claimed payment code " + claimMap.get("claimed"));
                                soroban.addSamouraiPaymentCode(newAuthToken, newSignature).subscribe(addMap -> {
                                    log.debug("Added payment code " + addMap);
                                });
                            });
                        }, newError -> {
                            log.error("Error claiming PayNym", newError);
                        });
                    });
                });
            }
        }, error -> {
            log.error("Error retrieving PayNym", error);
            AppServices.showErrorDialog("Error retrieving PayNym", error.getMessage());
        });
    }

    public void showPayNymQR(ActionEvent event) {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(soroban.getPaymentCode().toString());
        qrDisplayDialog.showAndWait();
    }

    public ObjectProperty<Boolean> meetingReceivedProperty() {
        return meetingReceived;
    }

    public ObjectProperty<Boolean> meetingAcceptedProperty() {
        return meetingAccepted;
    }

    public ObjectProperty<Transaction> transactionProperty() {
        return transactionProperty;
    }
}
