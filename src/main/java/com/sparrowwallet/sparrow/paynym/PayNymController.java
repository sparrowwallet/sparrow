package com.sparrowwallet.sparrow.paynym;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.bip47.SecretPoint;
import com.sparrowwallet.drongo.crypto.ECKey;
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
import javafx.beans.property.*;
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
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;
import static com.sparrowwallet.sparrow.wallet.PaymentController.MINIMUM_P2PKH_OUTPUT_SATS;

public class PayNymController {
    private static final Logger log = LoggerFactory.getLogger(PayNymController.class);

    public static final Pattern PAYNYM_REGEX = Pattern.compile("\\+[a-z]+[0-9][0-9a-fA-F][0-9a-fA-F]");
    public static final String INVALID_PAYMENT_CODE_LABEL = "Invalid Payment Code";

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
    private Button searchPayNymsScan;

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

    private final BooleanProperty closeProperty = new SimpleBooleanProperty(false);

    public void initializeView(String walletId, boolean selectLinkedOnly) {
        this.walletId = walletId;
        this.selectLinkedOnly = selectLinkedOnly;

        payNymName.managedProperty().bind(payNymName.visibleProperty());
        payNymRetrieve.managedProperty().bind(payNymRetrieve.visibleProperty());
        payNymRetrieve.visibleProperty().bind(payNymName.visibleProperty().not());
        payNymRetrieve.setDisable(!AppServices.isConnected());

        retrievePayNymProgress.managedProperty().bind(retrievePayNymProgress.visibleProperty());
        retrievePayNymProgress.maxHeightProperty().bind(payNymName.heightProperty());
        retrievePayNymProgress.setVisible(false);

        Wallet masterWallet = getMasterWallet();
        if(masterWallet.hasPaymentCode()) {
            paymentCode.setPaymentCode(masterWallet.getPaymentCode());
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
        searchPayNymsScan.disableProperty().bind(searchPayNyms.disableProperty());
        searchPayNyms.setDisable(true);
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
            return new PayNymCell(this, true);
        });

        followingList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, payNym) -> {
            payNymProperty.set(payNym);
        });

        followersList.setCellFactory(param -> {
            return new PayNymCell(this, false);
        });

        followersList.setSelectionModel(new NoSelectionModel<>());
        followersList.setFocusTraversable(false);

        if(isUsePayNym(masterWallet) && AppServices.isConnected() && masterWallet.hasPaymentCode()) {
            refresh();
        } else {
            payNymName.setVisible(false);
            updateFollowing();
        }
    }

    private void refresh() {
        if(!getMasterWallet().hasPaymentCode()) {
            throw new IllegalStateException("Payment code is not present");
        }
        retrievePayNymProgress.setVisible(true);

        PayNymService.getPayNym(getMasterWallet().getPaymentCode().toString()).subscribe(payNym -> {
            retrievePayNymProgress.setVisible(false);
            walletPayNym = payNym;
            searchPayNyms.setDisable(false);
            payNymName.setText(payNym.nymName());
            paymentCode.setPaymentCode(payNym.paymentCode());
            payNymAvatar.setPaymentCode(payNym.paymentCode());
            followingList.setUserData(null);
            followingList.setPlaceholder(new Label("No contacts"));
            updateFollowing();
            followersList.setPlaceholder(new Label("No followers"));
            followersList.setItems(FXCollections.observableList(payNym.followers()));
            Platform.runLater(() -> addWalletIfNotificationTransactionPresent(payNym.following()));
        }, error -> {
            retrievePayNymProgress.setVisible(false);
            if(error.getMessage().endsWith("404")) {
                payNymName.setVisible(false);
                updateFollowing();
            } else {
                log.error("Error retrieving PayNym", error);
                Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not retrieve PayNym. Try again?", ButtonType.CANCEL, ButtonType.OK);
                if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                    refresh();
                } else {
                    payNymName.setVisible(false);
                    updateFollowing();
                }
            }
        });
    }

    private void resetFollowing() {
        if(followingList.getUserData() != null) {
            followingList.setUserData(null);
            updateFollowing();
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

            PayNymService.getPayNym(nymIdentifier, true).subscribe(searchedPayNym -> {
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
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(getMasterWallet().getPaymentCode().toString());
        qrDisplayDialog.initOwner(payNymName.getScene().getWindow());
        qrDisplayDialog.showAndWait();
    }

    public void scanQR(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        qrScanDialog.initOwner(payNymName.getScene().getWindow());
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
        Wallet masterWallet = getMasterWallet();
        setUsePayNym(masterWallet, true);
        PayNymService.createPayNym(masterWallet).subscribe(createMap -> {
            payNymName.setText((String)createMap.get("nymName"));
            payNymAvatar.setPaymentCode(masterWallet.getPaymentCode());
            payNymName.setVisible(true);

            PayNymService.claimPayNym(masterWallet, createMap, getMasterWallet().getScriptType() != ScriptType.P2PKH);
            refresh();
        }, error -> {
            log.error("Error retrieving PayNym", error);
            Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not retrieve PayNym. Try again?", ButtonType.CANCEL, ButtonType.OK);
            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                retrievePayNym(event);
            }
        });
    }

    public void followPayNym(PaymentCode contact) {
        Wallet masterWallet = getMasterWallet();
        retrievePayNymProgress.setVisible(true);
        PayNymService.getAuthToken(masterWallet, new HashMap<>()).subscribe(authToken -> {
            String signature = PayNymService.getSignature(masterWallet, authToken);
            PayNymService.followPaymentCode(contact, authToken, signature).subscribe(followMap -> {
                refresh();
            }, error -> {
                retrievePayNymProgress.setVisible(false);
                log.error("Could not follow payment code", error);
                Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not follow payment code. Try again?", ButtonType.CANCEL, ButtonType.OK);
                if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                    followPayNym(contact);
                } else {
                    followingList.refresh();
                    followersList.refresh();
                }
            });
        }, error -> {
            retrievePayNymProgress.setVisible(false);
            log.error("Could not follow payment code", error);
            Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not follow payment code. Try again?", ButtonType.CANCEL, ButtonType.OK);
            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                followPayNym(contact);
            } else {
                followingList.refresh();
                followersList.refresh();
            }
        });
    }

    public Boolean isFollowing(PayNym payNym) {
        if(followingList.getItems() != null) {
            return followingList.getItems().stream().anyMatch(following -> payNym.paymentCode().equals(following.paymentCode()));
        }

        return null;
    }

    public boolean isLinked(PayNym payNym) {
        PaymentCode externalPaymentCode = payNym.paymentCode();
        //As per BIP47 a notification transaction must have been sent from this wallet to enable the recipient to restore funds from seed
        return getMasterWallet().getChildWallet(externalPaymentCode, payNym.segwit() ? ScriptType.P2WPKH : ScriptType.P2PKH) != null
                && !getNotificationTransaction(externalPaymentCode).isEmpty();
    }

    public void updateFollowing() {
        List<PayNym> followingPayNyms = new ArrayList<>();
        if(walletPayNym != null) {
            followingPayNyms.addAll(walletPayNym.following());
        }

        Map<PaymentCode, PayNym> payNymMap = followingPayNyms.stream().collect(Collectors.toMap(PayNym::paymentCode, Function.identity(), (u, v) -> u, LinkedHashMap::new));
        followingList.setItems(FXCollections.observableList(getExistingWalletPayNyms(payNymMap)));
    }

    private List<PayNym> getExistingWalletPayNyms(Map<PaymentCode, PayNym> payNymMap) {
        List<Wallet> childWallets = new ArrayList<>(getMasterWallet().getChildWallets());
        childWallets.sort(Comparator.comparingInt(o -> -o.getScriptType().ordinal()));
        for(Wallet childWallet : childWallets) {
            if(childWallet.isBip47()) {
                PaymentCode externalPaymentCode = childWallet.getKeystores().get(0).getExternalPaymentCode();
                String walletNymName = PayNym.getNymName(childWallet);
                if(payNymMap.get(externalPaymentCode) == null || (walletNymName != null && !walletNymName.equals(payNymMap.get(externalPaymentCode).nymName()))) {
                    payNymMap.put(externalPaymentCode, PayNym.fromWallet(childWallet));
                }
            }
        }

        return new ArrayList<>(payNymMap.values());
    }

    private void addWalletIfNotificationTransactionPresent(List<PayNym> following) {
        Map<BlockTransaction, PayNym> unlinkedPayNyms = new HashMap<>();
        Map<BlockTransaction, WalletNode> unlinkedNotifications = new HashMap<>();
        for(PayNym payNym : following) {
            if(!isLinked(payNym)) {
                PaymentCode externalPaymentCode = payNym.paymentCode();
                Map<BlockTransaction, WalletNode> unlinkedNotification = getNotificationTransaction(externalPaymentCode);
                if(!unlinkedNotification.isEmpty() && !INVALID_PAYMENT_CODE_LABEL.equals(unlinkedNotification.keySet().iterator().next().getLabel())) {
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
                    dlg.initOwner(payNymName.getScene().getWindow());
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
        List<Wallet> addedWallets = new ArrayList<>();
        for(BlockTransaction blockTransaction : unlinkedNotifications.keySet()) {
            try {
                PayNym payNym = unlinkedPayNyms.get(blockTransaction);
                PaymentCode externalPaymentCode = payNym.paymentCode();
                WalletNode input0Node = unlinkedNotifications.get(blockTransaction);
                Keystore keystore = input0Node.getWallet().isNested() ? decryptedWallet.getChildWallet(input0Node.getWallet().getName()).getKeystores().get(0) : decryptedWallet.getKeystores().get(0);
                ECKey input0Key = keystore.getKey(input0Node);
                TransactionOutPoint input0Outpoint = PaymentCode.getDesignatedInput(blockTransaction.getTransaction()).getOutpoint();
                SecretPoint secretPoint = new SecretPoint(input0Key.getPrivKeyBytes(), externalPaymentCode.getNotificationKey().getPubKey());
                byte[] blindingMask = PaymentCode.getMask(secretPoint.ECDHSecretAsBytes(), input0Outpoint.bitcoinSerialize());
                byte[] blindedPaymentCode = PaymentCode.blind(getMasterWallet().getPaymentCode().getPayload(), blindingMask);
                byte[] opReturnData = PaymentCode.getOpReturnData(blockTransaction.getTransaction());
                if(Arrays.equals(opReturnData, blindedPaymentCode)) {
                    addedWallets.addAll(addChildWallets(payNym, externalPaymentCode));
                    blockTransaction.setLabel("Link " + payNym.nymName());
                } else {
                    blockTransaction.setLabel(INVALID_PAYMENT_CODE_LABEL);
                }
                EventManager.get().post(new WalletEntryLabelsChangedEvent(input0Node.getWallet(), new TransactionEntry(input0Node.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap())));
            } catch(Exception e) {
                log.error("Error adding linked contact from notification transaction", e);
            }
        }

        if(!addedWallets.isEmpty()) {
            Wallet masterWallet = getMasterWallet();
            Storage storage = AppServices.get().getOpenWallets().get(masterWallet);
            EventManager.get().post(new ChildWalletsAddedEvent(storage, masterWallet, addedWallets));
            followingList.refresh();
        }
    }

    public List<Wallet> addChildWallets(PayNym payNym, PaymentCode externalPaymentCode) {
        List<Wallet> addedWallets = new ArrayList<>();
        Wallet masterWallet = getMasterWallet();
        Storage storage = AppServices.get().getOpenWallets().get(masterWallet);
        List<ScriptType> scriptTypes = masterWallet.getScriptType() != ScriptType.P2PKH ? PayNym.getSegwitScriptTypes() : payNym.getScriptTypes();
        for(ScriptType childScriptType : scriptTypes) {
            String label = payNym.nymName() + " " + childScriptType.getName();
            Wallet addedWallet = masterWallet.addChildWallet(externalPaymentCode, childScriptType, label);
            if(!storage.isPersisted(addedWallet)) {
                try {
                    storage.saveWallet(addedWallet);
                    EventManager.get().post(new NewChildWalletSavedEvent(storage, masterWallet, addedWallet));
                } catch(Exception e) {
                    log.error("Error saving wallet", e);
                    AppServices.showErrorDialog("Error saving wallet " + addedWallet.getName(), e.getMessage());
                }
            }
            addedWallets.add(addedWallet);
        }

        return addedWallets;
    }

    public void linkPayNym(PayNym payNym) {
        ButtonType previewType = new ButtonType("Preview", ButtonBar.ButtonData.LEFT);
        ButtonType sendType = new ButtonType("Send", ButtonBar.ButtonData.YES);
        Optional<ButtonType> optButtonType = AppServices.showAlertDialog("Link PayNym?",
                "Linking to this contact will allow you to send to it directly (non-collaboratively) through unique private addresses you can generate independently.\n\n" +
                "It will cost " + MINIMUM_P2PKH_OUTPUT_SATS + " sats to create the link through a notification transaction, plus the mining fee. Send transaction?", Alert.AlertType.CONFIRMATION, previewType, ButtonType.CANCEL, sendType);
        if(optButtonType.isPresent() && optButtonType.get() == sendType) {
            broadcastNotificationTransaction(payNym);
        } else if(optButtonType.isPresent() && optButtonType.get() == previewType) {
            PaymentCode paymentCode = payNym.paymentCode();
            Payment payment = new Payment(paymentCode.getNotificationAddress(), "Link " + payNym.nymName(), MINIMUM_P2PKH_OUTPUT_SATS, false);
            Wallet wallet = AppServices.get().getWallet(walletId);
            EventManager.get().post(new SendActionEvent(wallet, new ArrayList<>(wallet.getSpendableUtxos().keySet())));
            Platform.runLater(() -> EventManager.get().post(new SpendUtxoEvent(wallet, List.of(payment), List.of(new byte[80]), paymentCode)));
            closeProperty.set(true);
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
        final PaymentCode paymentCode = masterWallet.getPaymentCode();
        Wallet wallet = walletTransaction.getWallet();
        Storage storage = AppServices.get().getOpenWallets().get(wallet);
        if(wallet.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            dlg.initOwner(payNymName.getScene().getWindow());
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

    private void broadcastNotificationTransaction(Wallet decryptedWallet, WalletTransaction walletTransaction, PaymentCode paymentCode, PayNym payNym) {
        try {
            PaymentCode externalPaymentCode = payNym.paymentCode();
            WalletNode input0Node = walletTransaction.getSelectedUtxos().entrySet().iterator().next().getValue();
            Keystore keystore = input0Node.getWallet().isNested() ? decryptedWallet.getChildWallet(input0Node.getWallet().getName()).getKeystores().get(0) : decryptedWallet.getKeystores().get(0);
            ECKey input0Key = keystore.getKey(input0Node);
            TransactionOutPoint input0Outpoint = walletTransaction.getTransaction().getInputs().iterator().next().getOutpoint();
            SecretPoint secretPoint = new SecretPoint(input0Key.getPrivKeyBytes(), externalPaymentCode.getNotificationKey().getPubKey());
            byte[] blindingMask = PaymentCode.getMask(secretPoint.ECDHSecretAsBytes(), input0Outpoint.bitcoinSerialize());
            byte[] blindedPaymentCode = PaymentCode.blind(paymentCode.getPayload(), blindingMask);

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
                        List<Wallet> addedWallets = addChildWallets(payNym, externalPaymentCode);
                        Wallet masterWallet = getMasterWallet();
                        Storage storage = AppServices.get().getOpenWallets().get(masterWallet);
                        EventManager.get().post(new ChildWalletsAddedEvent(storage, masterWallet, addedWallets));
                        retrievePayNymProgress.setVisible(false);
                        followingList.refresh();

                        BlockTransaction blockTransaction = walletTransaction.getWallet().getWalletTransaction(transaction.getTxId());
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
        PaymentCode externalPaymentCode = payNym.paymentCode();
        Payment payment = new Payment(externalPaymentCode.getNotificationAddress(), "Link " + payNym.nymName(), MINIMUM_P2PKH_OUTPUT_SATS, false);
        List<Payment> payments = List.of(payment);
        List<byte[]> opReturns = List.of(blindedPaymentCode);
        Double feeRate = AppServices.getDefaultFeeRate();
        Double minimumFeeRate = AppServices.getMinimumFeeRate();
        boolean groupByAddress = Config.get().isGroupByAddress();
        boolean includeMempoolOutputs = Config.get().isIncludeMempoolOutputs();

        long noInputsFee = getMasterWallet().getNoInputsFee(payments, feeRate);
        List<UtxoSelector> utxoSelectors = List.of(utxos == null ? new KnapsackUtxoSelector(noInputsFee) : new PresetUtxoSelector(utxos, true, false));
        List<TxoFilter> txoFilters = List.of(new SpentTxoFilter(), new FrozenTxoFilter(), new CoinbaseTxoFilter(wallet));

        return wallet.createWalletTransaction(utxoSelectors, txoFilters, payments, opReturns, Collections.emptySet(), feeRate, minimumFeeRate, null, AppServices.getCurrentBlockHeight(), groupByAddress, includeMempoolOutputs);
    }

    private Map<BlockTransaction, WalletNode> getNotificationTransaction(PaymentCode externalPaymentCode) {
        Map<BlockTransaction, WalletNode> notificationTransaction = getMasterWallet().getNotificationTransaction(externalPaymentCode);
        if(notificationTransaction.isEmpty()) {
            for(Wallet childWallet : getMasterWallet().getChildWallets()) {
                if(!childWallet.isNested()) {
                    notificationTransaction = childWallet.getNotificationTransaction(externalPaymentCode);
                    if(!notificationTransaction.isEmpty()) {
                        return notificationTransaction;
                    }
                }
            }
        }

        return notificationTransaction;
    }

    public Wallet getMasterWallet() {
        Wallet wallet = AppServices.get().getWallet(walletId);
        return wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
    }

    protected boolean isUsePayNym(Wallet wallet) {
        //TODO: Remove config setting
        boolean usePayNym = Config.get().isUsePayNym();
        if(usePayNym && wallet != null) {
            setUsePayNym(wallet, true);
        }

        return usePayNym;
    }

    protected void setUsePayNym(Wallet wallet, boolean usePayNym) {
        //TODO: Remove config setting
        if(Config.get().isUsePayNym() != usePayNym) {
            Config.get().setUsePayNym(usePayNym);
        }

        if(wallet != null) {
            WalletConfig walletConfig = wallet.getMasterWalletConfig();
            if(walletConfig.isUsePayNym() != usePayNym) {
                walletConfig.setUsePayNym(usePayNym);
                EventManager.get().post(new WalletConfigChangedEvent(wallet.isMasterWallet() ? wallet : wallet.getMasterWallet()));
            }
        }
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

    public BooleanProperty closeProperty() {
        return closeProperty;
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        List<Entry> changedLabelEntries = new ArrayList<>();
        for(Map.Entry<Sha256Hash, PayNym> notificationTx : notificationTransactions.entrySet()) {
            BlockTransaction blockTransaction = event.getWallet().getWalletTransaction(notificationTx.getKey());
            if(blockTransaction != null && blockTransaction.getLabel() == null) {
                blockTransaction.setLabel("Link " + notificationTx.getValue().nymName());
                changedLabelEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
            }
        }

        if(!changedLabelEntries.isEmpty()) {
            Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(event.getWallet(), changedLabelEntries)));
        }

        if(walletPayNym != null) {
            //If we have just linked a PayNym wallet that paid for another notification transaction, attempt to link
            Platform.runLater(() -> addWalletIfNotificationTransactionPresent(walletPayNym.following()));
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
