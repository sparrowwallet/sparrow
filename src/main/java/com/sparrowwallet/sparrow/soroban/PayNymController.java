package com.sparrowwallet.sparrow.soroban;

import com.google.common.eventbus.Subscribe;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.bip47.SecretPoint;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.UnaryOperator;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class PayNymController extends SorobanController {
    private static final Logger log = LoggerFactory.getLogger(PayNymController.class);

    private static final long MINIMUM_P2PKH_OUTPUT_SATS = 546L;

    private String walletId;
    private boolean selectLinkedOnly;
    private PayNym walletPayNym;

    @FXML
    private CopyableTextField payNymName;

    @FXML
    private Button payNymRetrieve;

    @FXML
    private ProgressIndicator retrievePayNymProgress;

    @FXML
    private PaymentCodeTextField paymentCode;

    @FXML
    private CopyableTextField searchPayNyms;

    @FXML
    private ProgressIndicator findPayNym;

    @FXML
    private PayNymAvatar payNymAvatar;

    @FXML
    private ListView<PayNym> followingList;

    @FXML
    private ListView<PayNym> followersList;

    private final ObjectProperty<PayNym> payNymProperty = new SimpleObjectProperty<>(null);

    private final StringProperty findNymProperty = new SimpleStringProperty();

    private final Map<Sha256Hash, PayNym> notificationTransactions = new HashMap<>();

    public void initializeView(String walletId, boolean selectLinkedOnly) {
        this.walletId = walletId;
        this.selectLinkedOnly = selectLinkedOnly;

        payNymName.managedProperty().bind(payNymName.visibleProperty());
        payNymRetrieve.managedProperty().bind(payNymRetrieve.visibleProperty());
        payNymRetrieve.visibleProperty().bind(payNymName.visibleProperty().not());

        retrievePayNymProgress.managedProperty().bind(retrievePayNymProgress.visibleProperty());
        retrievePayNymProgress.maxHeightProperty().bind(payNymName.heightProperty());
        retrievePayNymProgress.setVisible(false);

        Wallet masterWallet = getMasterWallet();
        if(masterWallet.hasPaymentCode()) {
            paymentCode.setPaymentCode(new PaymentCode(masterWallet.getPaymentCode().toString()));
        }

        findNymProperty.addListener((observable, oldValue, nymIdentifier) -> {
            if(nymIdentifier != null) {
                searchFollowing(nymIdentifier);
            }
        });

        UnaryOperator<TextFormatter.Change> paymentCodeFilter = change -> {
            String input = change.getControlNewText();
            if(input.startsWith("P") && !input.contains("...")) {
                try {
                    PaymentCode paymentCode = new PaymentCode(input);
                    if(paymentCode.isValid()) {
                        findNymProperty.set(input);

                        TextInputControl control = (TextInputControl)change.getControl();
                        change.setText(input.substring(0, 12) + "..." + input.substring(input.length() - 5));
                        change.setRange(0, control.getLength());
                        change.setAnchor(change.getText().length());
                        change.setCaretPosition(change.getText().length());
                    }
                } catch(Exception e) {
                    //ignore
                }
            } else if(PAYNYM_REGEX.matcher(input).matches()) {
                findNymProperty.set(input);
            } else {
                findNymProperty.set(null);
                resetFollowing();
            }

            return change;
        };
        searchPayNyms.setTextFormatter(new TextFormatter<>(paymentCodeFilter));
        searchPayNyms.addEventFilter(KeyEvent.ANY, event -> {
            if(event.getCode() == KeyCode.ENTER) {
                findNymProperty.set(searchPayNyms.getText());
                event.consume();
            }
        });
        findPayNym.managedProperty().bind(findPayNym.visibleProperty());
        findPayNym.maxHeightProperty().bind(searchPayNyms.heightProperty());
        findPayNym.setVisible(false);

        followingList.setCellFactory(param -> {
            return new PayNymCell(this);
        });

        followingList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, payNym) -> {
            payNymProperty.set(payNym);
        });

        followersList.setCellFactory(param -> {
            return new PayNymCell(null);
        });

        followersList.setSelectionModel(new NoSelectionModel<>());
        followersList.setFocusTraversable(false);

        if(Config.get().isUsePayNym() && masterWallet.hasPaymentCode()) {
            refresh();
        } else {
            payNymName.setVisible(false);
        }
    }

    private void refresh() {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(!getMasterWallet().hasPaymentCode()) {
            throw new IllegalStateException("Payment code is not present");
        }
        retrievePayNymProgress.setVisible(true);

        soroban.getPayNym(getMasterWallet().getPaymentCode().toString()).subscribe(payNym -> {
            retrievePayNymProgress.setVisible(false);
            walletPayNym = payNym;
            payNymName.setText(payNym.nymName());
            paymentCode.setPaymentCode(payNym.paymentCode());
            payNymAvatar.setPaymentCode(payNym.paymentCode());
            followingList.setUserData(null);
            followingList.setPlaceholder(new Label("No contacts"));
            followingList.setItems(FXCollections.observableList(payNym.following()));
            followersList.setPlaceholder(new Label("No followers"));
            followersList.setItems(FXCollections.observableList(payNym.followers()));
            Platform.runLater(() -> addWalletIfNotificationTransactionPresent(payNym.following()));
        }, error -> {
            retrievePayNymProgress.setVisible(false);
            if(error.getMessage().endsWith("404")) {
                payNymName.setVisible(false);
            } else {
                log.error("Error retrieving PayNym", error);
                Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not retrieve PayNym. Try again?", ButtonType.CANCEL, ButtonType.OK);
                if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                    refresh();
                } else {
                    payNymName.setVisible(false);
                }
            }
        });
    }

    private void resetFollowing() {
        if(followingList.getUserData() != null) {
            followingList.setUserData(null);
            followingList.setItems(FXCollections.observableList(walletPayNym.following()));
        }
    }

    private void searchFollowing(String nymIdentifier) {
        Optional<PayNym> optExisting = walletPayNym.following().stream().filter(payNym -> payNym.nymName().equals(nymIdentifier) || payNym.paymentCode().toString().equals(nymIdentifier)).findFirst();
        if(optExisting.isPresent()) {
            followingList.setUserData(Boolean.FALSE);
            List<PayNym> existingPayNym = new ArrayList<>();
            existingPayNym.add(optExisting.get());
            followingList.setItems(FXCollections.observableList(existingPayNym));
        } else {
            followingList.setUserData(Boolean.TRUE);
            followingList.setItems(FXCollections.observableList(new ArrayList<>()));
            findPayNym.setVisible(true);

            Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
            soroban.getPayNym(nymIdentifier).subscribe(searchedPayNym -> {
                findPayNym.setVisible(false);
                List<PayNym> searchList = new ArrayList<>();
                searchList.add(searchedPayNym);
                followingList.setUserData(Boolean.TRUE);
                followingList.setItems(FXCollections.observableList(searchList));
            }, error -> {
                findPayNym.setVisible(false);
            });
        }
    }

    public void showQR(ActionEvent event) {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(getMasterWallet().getPaymentCode().toString());
        qrDisplayDialog.showAndWait();
    }

    public void scanQR(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        Optional<QRScanDialog.Result> optResult = qrScanDialog.showAndWait();
        if(optResult.isPresent()) {
            QRScanDialog.Result result = optResult.get();
            if(result.payload != null) {
                searchPayNyms.setText(result.payload);
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "Cannot parse QR code into a payment code");
            }
        }
    }

    public void retrievePayNym(ActionEvent event) {
        Config.get().setUsePayNym(true);
        makeAuthenticatedCall(null);
    }

    public void followPayNym(PaymentCode paymentCode) {
        makeAuthenticatedCall(paymentCode);
    }

    private void makeAuthenticatedCall(PaymentCode contact) {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getHdWallet() == null) {
            Wallet wallet = getMasterWallet();
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
                            makeAuthenticatedCall(soroban, contact);
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
                                Platform.runLater(() -> makeAuthenticatedCall(contact));
                            }
                        } else {
                            log.error("Error deriving wallet key", keyDerivationService.getException());
                        }
                    });
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                    keyDerivationService.start();
                }
            } else {
                soroban.setHDWallet(wallet);
                makeAuthenticatedCall(soroban, contact);
            }
        } else {
            makeAuthenticatedCall(soroban, contact);
        }
    }

    private void makeAuthenticatedCall(Soroban soroban, PaymentCode contact) {
        if(contact != null) {
            followPayNym(soroban, contact);
        } else {
            retrievePayNym(soroban);
        }
    }

    private void retrievePayNym(Soroban soroban) {
        soroban.createPayNym().subscribe(createMap -> {
            payNymName.setText((String)createMap.get("nymName"));
            payNymAvatar.setPaymentCode(new PaymentCode(getMasterWallet().getPaymentCode().toString()));
            payNymName.setVisible(true);

            claimPayNym(soroban, createMap, getMasterWallet().getScriptType() != ScriptType.P2PKH);
            refresh();
        }, error -> {
            log.error("Error retrieving PayNym", error);
            Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not retrieve PayNym. Try again?", ButtonType.CANCEL, ButtonType.OK);
            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                retrievePayNym(soroban);
            }
        });
    }

    private void followPayNym(Soroban soroban, PaymentCode contact) {
        soroban.getAuthToken(new HashMap<>()).subscribe(authToken -> {
            String signature = soroban.getSignature(authToken);
            soroban.followPaymentCode(contact, authToken, signature).subscribe(followMap -> {
                refresh();
            }, error -> {
                log.error("Could not follow payment code", error);
                Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not follow payment code. Try again?", ButtonType.CANCEL, ButtonType.OK);
                if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                    followPayNym(soroban, contact);
                } else {
                    followingList.refresh();
                }
            });
        }, error -> {
            log.error("Could not follow payment code", error);
            Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not follow payment code. Try again?", ButtonType.CANCEL, ButtonType.OK);
            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                followPayNym(soroban, contact);
            } else {
                followingList.refresh();
            }
        });
    }

    public boolean isLinked(PayNym payNym) {
        com.sparrowwallet.drongo.bip47.PaymentCode externalPaymentCode = com.sparrowwallet.drongo.bip47.PaymentCode.fromString(payNym.paymentCode().toString());
        return getMasterWallet().getChildWallet(externalPaymentCode, payNym.segwit() ? ScriptType.P2WPKH : ScriptType.P2PKH) != null;
    }

    private void addWalletIfNotificationTransactionPresent(List<PayNym> following) {
        Map<BlockTransaction, PayNym> unlinkedPayNyms = new HashMap<>();
        Map<BlockTransaction, WalletNode> unlinkedNotifications = new HashMap<>();
        for(PayNym payNym : following) {
            if(!isLinked(payNym)) {
                com.sparrowwallet.drongo.bip47.PaymentCode externalPaymentCode = com.sparrowwallet.drongo.bip47.PaymentCode.fromString(payNym.paymentCode().toString());
                Map<BlockTransaction, WalletNode> unlinkedNotification = getMasterWallet().getNotificationTransaction(externalPaymentCode);
                if(!unlinkedNotification.isEmpty()) {
                    unlinkedNotifications.putAll(unlinkedNotification);
                    unlinkedPayNyms.put(unlinkedNotification.keySet().iterator().next(), payNym);
                }
            }
        }

        Wallet wallet = getMasterWallet();
        if(!unlinkedNotifications.isEmpty()) {
            if(wallet.isEncrypted()) {
                Storage storage = AppServices.get().getOpenWallets().get(wallet);
                Optional<ButtonType> optButtonType = AppServices.showAlertDialog("Link contacts?", "Some contacts were found that may be already linked. Link these contacts? Your password is required to check.", Alert.AlertType.CONFIRMATION, ButtonType.NO, ButtonType.YES);
                if(optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
                    WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                    Optional<SecureString> password = dlg.showAndWait();
                    if(password.isPresent()) {
                        Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(wallet.copy(), password.get());
                        decryptWalletService.setOnSucceeded(workerStateEvent -> {
                            EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Done"));
                            Wallet decryptedWallet = decryptWalletService.getValue();
                            addWalletIfNotificationTransactionPresent(decryptedWallet, unlinkedPayNyms, unlinkedNotifications);
                            decryptedWallet.clearPrivate();
                        });
                        decryptWalletService.setOnFailed(workerStateEvent -> {
                            EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Failed"));
                            AppServices.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                        });
                        EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.START, "Decrypting wallet..."));
                        decryptWalletService.start();
                    }
                }
            } else {
                addWalletIfNotificationTransactionPresent(wallet, unlinkedPayNyms, unlinkedNotifications);
            }
        }
    }

    private void addWalletIfNotificationTransactionPresent(Wallet decryptedWallet, Map<BlockTransaction, PayNym> unlinkedPayNyms, Map<BlockTransaction, WalletNode> unlinkedNotifications) {
        for(BlockTransaction blockTransaction : unlinkedNotifications.keySet()) {
            try {
                PayNym payNym = unlinkedPayNyms.get(blockTransaction);
                com.sparrowwallet.drongo.bip47.PaymentCode externalPaymentCode = com.sparrowwallet.drongo.bip47.PaymentCode.fromString(payNym.paymentCode().toString());
                ECKey input0Key = decryptedWallet.getKeystores().get(0).getKey(unlinkedNotifications.get(blockTransaction));
                TransactionOutPoint input0Outpoint = com.sparrowwallet.drongo.bip47.PaymentCode.getDesignatedInput(blockTransaction.getTransaction()).getOutpoint();
                SecretPoint secretPoint = new SecretPoint(input0Key.getPrivKeyBytes(), externalPaymentCode.getNotificationKey().getPubKey());
                byte[] blindingMask = com.sparrowwallet.drongo.bip47.PaymentCode.getMask(secretPoint.ECDHSecretAsBytes(), input0Outpoint.bitcoinSerialize());
                byte[] blindedPaymentCode = com.sparrowwallet.drongo.bip47.PaymentCode.blind(getMasterWallet().getPaymentCode().getPayload(), blindingMask);
                byte[] opReturnData = com.sparrowwallet.drongo.bip47.PaymentCode.getOpReturnData(blockTransaction.getTransaction());
                if(Arrays.equals(opReturnData, blindedPaymentCode)) {
                    addChildWallet(payNym, externalPaymentCode);
                    followingList.refresh();
                }
            } catch(Exception e) {
                log.error("Error adding linked contact from notification transaction", e);
            }
        }
    }

    public void addChildWallet(PayNym payNym, com.sparrowwallet.drongo.bip47.PaymentCode externalPaymentCode) {
        Wallet masterWallet = getMasterWallet();
        Storage storage = AppServices.get().getOpenWallets().get(masterWallet);
        List<ScriptType> scriptTypes = masterWallet.getScriptType() != ScriptType.P2PKH ? PayNym.getSegwitScriptTypes() : payNym.getScriptTypes();
        for(ScriptType childScriptType : scriptTypes) {
            Wallet addedWallet = masterWallet.addChildWallet(externalPaymentCode, childScriptType);
            addedWallet.setLabel(payNym.nymName() + " " + childScriptType.getName());
            if(!storage.isPersisted(addedWallet)) {
                try {
                    storage.saveWallet(addedWallet);
                } catch(Exception e) {
                    log.error("Error saving wallet", e);
                    AppServices.showErrorDialog("Error saving wallet " + addedWallet.getName(), e.getMessage());
                }
            }
            EventManager.get().post(new ChildWalletAddedEvent(storage, masterWallet, addedWallet));
        }
    }

    public void linkPayNym(PayNym payNym) {
        Optional<ButtonType> optButtonType = AppServices.showAlertDialog("Link PayNym?",
                "Linking to this contact will allow you to send to it non-collaboratively through unique private addresses you can generate independently.\n\n" +
                "It will cost " + MINIMUM_P2PKH_OUTPUT_SATS + " sats to create the link, plus the mining fee. Send transaction?", Alert.AlertType.CONFIRMATION, ButtonType.NO, ButtonType.YES);
        if(optButtonType.isPresent() && optButtonType.get() == ButtonType.YES) {
            broadcastNotificationTransaction(payNym);
        } else {
            followingList.refresh();
        }
    }

    public void broadcastNotificationTransaction(PayNym payNym) {
        Wallet masterWallet = getMasterWallet();
        WalletTransaction walletTransaction;
        try {
            walletTransaction = getWalletTransaction(masterWallet, payNym, new byte[80], null);
        } catch(InsufficientFundsException e) {
            try {
                Wallet wallet = AppServices.get().getWallet(walletId);
                walletTransaction = getWalletTransaction(wallet, payNym, new byte[80], null);
            } catch(InsufficientFundsException e2) {
                AppServices.showErrorDialog("Insufficient Funds", "There are not enough funds in this wallet to broadcast the notification transaction.");
                followingList.refresh();
                return;
            }
        }

        final WalletTransaction walletTx = walletTransaction;
        final com.sparrowwallet.drongo.bip47.PaymentCode paymentCode = masterWallet.getPaymentCode();
        Wallet wallet = walletTransaction.getWallet();
        Storage storage = AppServices.get().getOpenWallets().get(wallet);
        if(wallet.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(wallet.copy(), password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    broadcastNotificationTransaction(decryptedWallet, walletTx, paymentCode, payNym);
                    decryptedWallet.clearPrivate();
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.END, "Failed"));
                    followingList.refresh();
                    AppServices.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(storage.getWalletId(wallet), TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            broadcastNotificationTransaction(wallet, walletTx, paymentCode, payNym);
        }
    }

    private void broadcastNotificationTransaction(Wallet decryptedWallet, WalletTransaction walletTransaction, com.sparrowwallet.drongo.bip47.PaymentCode paymentCode, PayNym payNym) {
        try {
            com.sparrowwallet.drongo.bip47.PaymentCode externalPaymentCode = com.sparrowwallet.drongo.bip47.PaymentCode.fromString(payNym.paymentCode().toString());
            WalletNode input0Node = walletTransaction.getSelectedUtxos().entrySet().iterator().next().getValue();
            ECKey input0Key = decryptedWallet.getKeystores().get(0).getKey(input0Node);
            TransactionOutPoint input0Outpoint = walletTransaction.getTransaction().getInputs().iterator().next().getOutpoint();
            SecretPoint secretPoint = new SecretPoint(input0Key.getPrivKeyBytes(), externalPaymentCode.getNotificationKey().getPubKey());
            byte[] blindingMask = com.sparrowwallet.drongo.bip47.PaymentCode.getMask(secretPoint.ECDHSecretAsBytes(), input0Outpoint.bitcoinSerialize());
            byte[] blindedPaymentCode = com.sparrowwallet.drongo.bip47.PaymentCode.blind(paymentCode.getPayload(), blindingMask);

            WalletTransaction finalWalletTx = getWalletTransaction(decryptedWallet, payNym, blindedPaymentCode, walletTransaction.getSelectedUtxos().keySet());
            PSBT psbt = finalWalletTx.createPSBT();
            decryptedWallet.sign(psbt);
            decryptedWallet.finalise(psbt);
            Transaction transaction = psbt.extractTransaction();

            ElectrumServer.BroadcastTransactionService broadcastTransactionService = new ElectrumServer.BroadcastTransactionService(transaction);
            broadcastTransactionService.setOnSucceeded(successEvent -> {
                ElectrumServer.TransactionMempoolService transactionMempoolService = new ElectrumServer.TransactionMempoolService(walletTransaction.getWallet(), transaction.getTxId(), new HashSet<>(walletTransaction.getSelectedUtxos().values()));
                transactionMempoolService.setDelay(Duration.seconds(2));
                transactionMempoolService.setPeriod(Duration.seconds(5));
                transactionMempoolService.setRestartOnFailure(false);
                transactionMempoolService.setOnSucceeded(mempoolWorkerStateEvent -> {
                    Set<String> scriptHashes = transactionMempoolService.getValue();
                    if(!scriptHashes.isEmpty()) {
                        transactionMempoolService.cancel();
                        addChildWallet(payNym, externalPaymentCode);
                        retrievePayNymProgress.setVisible(false);
                        followingList.refresh();

                        BlockTransaction blockTransaction = walletTransaction.getWallet().getTransactions().get(transaction.getTxId());
                        if(blockTransaction != null && blockTransaction.getLabel() == null) {
                            blockTransaction.setLabel("Link " + payNym.nymName());
                            TransactionEntry transactionEntry = new TransactionEntry(walletTransaction.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap());
                            EventManager.get().post(new WalletEntryLabelsChangedEvent(walletTransaction.getWallet(), List.of(transactionEntry)));
                        }
                    }

                    if(transactionMempoolService.getIterationCount() > 5 && transactionMempoolService.isRunning()) {
                        transactionMempoolService.cancel();
                        retrievePayNymProgress.setVisible(false);
                        followingList.refresh();
                        log.error("Timeout searching for broadcasted notification transaction");
                        AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not register it in the mempool. It is safe to try linking again.");
                    }
                });
                transactionMempoolService.setOnFailed(mempoolWorkerStateEvent -> {
                    transactionMempoolService.cancel();
                    log.error("Error searching for broadcasted notification transaction", mempoolWorkerStateEvent.getSource().getException());
                    retrievePayNymProgress.setVisible(false);
                    followingList.refresh();
                    AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not register it in the mempool. It is safe to try linking again.");
                });
                transactionMempoolService.start();
            });
            broadcastTransactionService.setOnFailed(failedEvent -> {
                log.error("Error broadcasting notification transaction", failedEvent.getSource().getException());
                retrievePayNymProgress.setVisible(false);
                followingList.refresh();
                AppServices.showErrorDialog("Error broadcasting notification transaction", failedEvent.getSource().getException().getMessage());
            });
            retrievePayNymProgress.setVisible(true);
            notificationTransactions.put(transaction.getTxId(), payNym);
            broadcastTransactionService.start();
        } catch(Exception e) {
            log.error("Error creating notification transaction", e);
            retrievePayNymProgress.setVisible(false);
            followingList.refresh();
            AppServices.showErrorDialog("Error creating notification transaction", e.getMessage());
        }
    }

    private WalletTransaction getWalletTransaction(Wallet wallet, PayNym payNym, byte[] blindedPaymentCode, Collection<BlockTransactionHashIndex> utxos) throws InsufficientFundsException {
        com.sparrowwallet.drongo.bip47.PaymentCode externalPaymentCode = com.sparrowwallet.drongo.bip47.PaymentCode.fromString(payNym.paymentCode().toString());
        Payment payment = new Payment(externalPaymentCode.getNotificationAddress(), "Link " + payNym.nymName(), MINIMUM_P2PKH_OUTPUT_SATS, false);
        List<Payment> payments = List.of(payment);
        List<byte[]> opReturns = List.of(blindedPaymentCode);
        Double feeRate = AppServices.getDefaultFeeRate();
        Double minimumFeeRate = AppServices.getMinimumFeeRate();
        boolean groupByAddress = Config.get().isGroupByAddress();
        boolean includeMempoolOutputs = Config.get().isIncludeMempoolOutputs();

        long noInputsFee = getMasterWallet().getNoInputsFee(payments, feeRate);
        List<UtxoSelector> utxoSelectors = List.of(utxos == null ? new KnapsackUtxoSelector(noInputsFee) : new PresetUtxoSelector(utxos, true));
        List<UtxoFilter> utxoFilters = List.of(new FrozenUtxoFilter(), new CoinbaseUtxoFilter(wallet));

        return wallet.createWalletTransaction(utxoSelectors, utxoFilters, payments, opReturns, Collections.emptySet(), feeRate, minimumFeeRate, null, AppServices.getCurrentBlockHeight(), groupByAddress, includeMempoolOutputs, false);
    }

    private Wallet getMasterWallet() {
        Wallet wallet = AppServices.get().getWallet(walletId);
        return wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
    }

    public boolean isSelectLinkedOnly() {
        return selectLinkedOnly;
    }

    public PayNym getPayNym() {
        return payNymProperty.get();
    }

    public ObjectProperty<PayNym> payNymProperty() {
        return payNymProperty;
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        List<Entry> changedLabelEntries = new ArrayList<>();
        for(Map.Entry<Sha256Hash, PayNym> notificationTx : notificationTransactions.entrySet()) {
            BlockTransaction blockTransaction = event.getWallet().getTransactions().get(notificationTx.getKey());
            if(blockTransaction != null && blockTransaction.getLabel() == null) {
                blockTransaction.setLabel("Link " + notificationTx.getValue().nymName());
                changedLabelEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
            }
        }

        if(!changedLabelEntries.isEmpty()) {
            Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(event.getWallet(), changedLabelEntries)));
        }
    }

    public static class NoSelectionModel<T> extends MultipleSelectionModel<T> {

        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return FXCollections.emptyObservableList();
        }

        @Override
        public ObservableList<T> getSelectedItems() {
            return FXCollections.emptyObservableList();
        }

        @Override
        public void selectIndices(int index, int... indices) {
        }

        @Override
        public void selectAll() {
        }

        @Override
        public void selectFirst() {
        }

        @Override
        public void selectLast() {
        }

        @Override
        public void clearAndSelect(int index) {
        }

        @Override
        public void select(int index) {
        }

        @Override
        public void select(T obj) {
        }

        @Override
        public void clearSelection(int index) {
        }

        @Override
        public void clearSelection() {
        }

        @Override
        public boolean isSelected(int index) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void selectPrevious() {
        }

        @Override
        public void selectNext() {
        }
    }
}
