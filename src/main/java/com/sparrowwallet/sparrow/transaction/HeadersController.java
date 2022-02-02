package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.payjoin.Payjoin;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.DateCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.DateTimePicker;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import com.google.common.eventbus.Subscribe;
import tornadofx.control.Form;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class HeadersController extends TransactionFormController implements Initializable, DynamicUpdate {
    private static final Logger log = LoggerFactory.getLogger(HeadersController.class);
    public static final String LOCKTIME_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String BLOCK_TIMESTAMP_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss ZZZ";
    public static final String UNFINALIZED_TXID_CLASS = "unfinalized-txid";

    public static final String MAX_LOCKTIME_DATE = "2106-02-07T06:28:15Z";
    public static final String MIN_LOCKTIME_DATE = "1985-11-05T00:53:20Z";

    private HeadersForm headersForm;

    @FXML
    private IdLabel id;

    @FXML
    private TransactionDiagram transactionDiagram;

    @FXML
    private Spinner<Integer> version;

    @FXML
    private CopyableLabel segwit;

    @FXML
    private ToggleGroup locktimeToggleGroup;

    @FXML
    private ToggleButton locktimeNoneType;

    @FXML
    private ToggleButton locktimeBlockType;

    @FXML
    private ToggleButton locktimeDateType;

    @FXML
    private Fieldset locktimeFieldset;

    @FXML
    private Field locktimeNoneField;

    @FXML
    private Field locktimeBlockField;

    @FXML
    private Field locktimeDateField;

    @FXML
    private Spinner<Integer> locktimeNone;

    @FXML
    private Spinner<Integer> locktimeBlock;

    @FXML
    private Hyperlink locktimeCurrentHeight;

    @FXML
    private Label futureBlockWarning;

    @FXML
    private DateTimePicker locktimeDate;

    @FXML
    private Label futureDateWarning;

    @FXML
    private CopyableLabel size;

    @FXML
    private CopyableLabel virtualSize;

    @FXML
    private CopyableCoinLabel fee;

    @FXML
    private CopyableLabel feeRate;

    @FXML
    private DynamicForm blockchainForm;

    @FXML
    private Label blockStatus;

    @FXML
    private Field blockHeightField;

    @FXML
    private CopyableLabel blockHeight;

    @FXML
    private Field blockTimestampField;

    @FXML
    private CopyableLabel blockTimestamp;

    @FXML
    private Field blockHashField;

    @FXML
    private IdLabel blockHash;

    @FXML
    private Form signingWalletForm;

    @FXML
    private ComboBox<Wallet> signingWallet;

    @FXML
    private Label noWalletsWarning;

    @FXML
    private Hyperlink noWalletsWarningLink;

    @FXML
    private Form sigHashForm;

    @FXML
    private ComboBox<SigHash> sigHash;

    @FXML
    private VBox finalizeButtonBox;

    @FXML
    private Button finalizeTransaction;

    @FXML
    private Form signaturesForm;

    @FXML
    private SignaturesProgressBar signaturesProgressBar;

    @FXML
    private ProgressBar broadcastProgressBar;

    @FXML
    private HBox signButtonBox;

    @FXML
    private Button signButton;

    @FXML
    private HBox broadcastButtonBox;

    @FXML
    private Button viewFinalButton;

    @FXML
    private Button broadcastButton;

    @FXML
    private Button saveFinalButton;

    @FXML
    private Button payjoinButton;

    private ElectrumServer.TransactionMempoolService transactionMempoolService;

    private final Map<Integer, String> outputIndexLabels = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    void setModel(HeadersForm form) {
        this.headersForm = form;
        initializeView();
    }

    @Override
    protected TransactionForm getTransactionForm() {
        return headersForm;
    }

    private void initializeView() {
        Transaction tx = headersForm.getTransaction();

        updateTxId();

        version.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2, (int)tx.getVersion()));
        version.valueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setVersion(newValue);
            if(oldValue != null) {
                EventManager.get().post(new TransactionChangedEvent(tx));
            }
        });
        version.setDisable(!headersForm.isEditable());

        updateType();

        locktimeToggleGroup.selectedToggleProperty().addListener((ov, old_toggle, new_toggle) -> {
            if(locktimeToggleGroup.getSelectedToggle() != null) {
                String selection = locktimeToggleGroup.getSelectedToggle().getUserData().toString();
                if(selection.equals("none")) {
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().remove(locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeNoneField);
                    tx.setLocktime(0);
                    if(old_toggle != null) {
                        EventManager.get().post(new TransactionChangedEvent(tx));
                    }
                } else if(selection.equals("block")) {
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().remove(locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeBlockField);
                    Integer block = locktimeBlock.getValue();
                    if(block != null) {
                        locktimeCurrentHeight.setVisible(headersForm.isEditable() && AppServices.getCurrentBlockHeight() != null && block < AppServices.getCurrentBlockHeight());
                        futureBlockWarning.setVisible(AppServices.getCurrentBlockHeight() != null && block > AppServices.getCurrentBlockHeight());
                        tx.setLocktime(block);
                        if(old_toggle != null) {
                            EventManager.get().post(new TransactionChangedEvent(tx));
                        }
                    }
                } else {
                    locktimeFieldset.getChildren().remove(locktimeBlockField);
                    locktimeFieldset.getChildren().remove(locktimeDateField);
                    locktimeFieldset.getChildren().remove(locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeDateField);
                    LocalDateTime date = locktimeDate.getDateTimeValue();
                    if(date != null) {
                        locktimeDate.setDateTimeValue(date);
                        futureDateWarning.setVisible(date.isAfter(LocalDateTime.now()));
                        tx.setLocktime(date.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
                        if(old_toggle != null) {
                            EventManager.get().post(new TransactionChangedEvent(tx));
                        }
                    }
                }
            }
        });

        locktimeCurrentHeight.managedProperty().bind(locktimeCurrentHeight.visibleProperty());
        locktimeCurrentHeight.setVisible(false);
        futureBlockWarning.managedProperty().bind(futureBlockWarning.visibleProperty());
        futureBlockWarning.setVisible(false);
        futureDateWarning.managedProperty().bind(futureDateWarning.visibleProperty());
        futureDateWarning.setVisible(false);

        locktimeNone.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1, 0));
        if(tx.getLocktime() < Transaction.MAX_BLOCK_LOCKTIME) {
            locktimeBlock.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1, (int)tx.getLocktime()));
            if(tx.getLocktime() == 0) {
                locktimeToggleGroup.selectToggle(locktimeNoneType);
            } else {
                locktimeToggleGroup.selectToggle(locktimeBlockType);
            }
            LocalDateTime date = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDate.setDateTimeValue(date);
        } else {
            locktimeBlock.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1));
            LocalDateTime date = Instant.ofEpochSecond(tx.getLocktime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDate.setDateTimeValue(date);
            locktimeToggleGroup.selectToggle(locktimeDateType);
        }

        locktimeBlock.valueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLocktime(newValue);
            locktimeCurrentHeight.setVisible(headersForm.isEditable() && AppServices.getCurrentBlockHeight() != null && newValue < AppServices.getCurrentBlockHeight());
            futureBlockWarning.setVisible(AppServices.getCurrentBlockHeight() != null && newValue > AppServices.getCurrentBlockHeight());
            if(oldValue != null) {
                EventManager.get().post(new TransactionChangedEvent(tx));
                EventManager.get().post(new TransactionLocktimeChangedEvent(tx));
            }
        });

        LocalDateTime maxLocktimeDate = Instant.parse(MAX_LOCKTIME_DATE).atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime minLocktimeDate = Instant.parse(MIN_LOCKTIME_DATE).atZone(ZoneId.systemDefault()).toLocalDateTime();
        locktimeDate.setDayCellFactory(d ->
                new DateCell() {
                    @Override
                    public void updateItem(LocalDate item, boolean empty) {
                        super.updateItem(item, empty);
                        setDisable(item.isAfter(maxLocktimeDate.toLocalDate()) || item.isBefore(minLocktimeDate.toLocalDate()));
                    }
                });

        locktimeDate.setFormat(LOCKTIME_DATE_FORMAT);
        locktimeDate.dateTimeValueProperty().addListener((obs, oldValue, newValue) -> {
            int caret = locktimeDate.getEditor().getCaretPosition();
            locktimeDate.getEditor().setText(newValue.format(DateTimeFormatter.ofPattern(locktimeDate.getFormat())));
            locktimeDate.getEditor().positionCaret(caret);
            tx.setLocktime(newValue.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
            futureDateWarning.setVisible(newValue.isAfter(LocalDateTime.now()));
            if(oldValue != null) {
                EventManager.get().post(new TransactionChangedEvent(tx));
            }
        });

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(LOCKTIME_DATE_FORMAT);
        locktimeDate.setConverter(new StringConverter<LocalDate>() {
            public String toString(LocalDate object) {
                LocalDateTime value = locktimeDate.getDateTimeValue();
                return (value != null) ? value.format(formatter) : "";
            }

            public LocalDate fromString(String value) {
                if(value == null) {
                    locktimeDate.setDateTimeValue(null);
                    return null;
                }

                LocalDateTime localDateTime = LocalDateTime.parse(value, formatter);
                if(localDateTime.isAfter(maxLocktimeDate) || localDateTime.isBefore(minLocktimeDate)) {
                    throw new IllegalArgumentException("Invalid locktime date");
                }
                locktimeDate.setDateTimeValue(localDateTime);
                return locktimeDate.getDateTimeValue().toLocalDate();
            }
        });

        locktimeDate.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            String controlValue = locktimeDate.getDateTimeValue().format(DateTimeFormatter.ofPattern(locktimeDate.getFormat()));
            if(!controlValue.equals(newValue) && !locktimeDate.getStyleClass().contains("edited")) {
                locktimeDate.getStyleClass().add("edited");
            }
        });

        locktimeDate.addEventFilter(KeyEvent.ANY, event -> {
            if(event.getCode() == KeyCode.ENTER) {
                locktimeDate.getStyleClass().remove("edited");
            }
        });

        boolean locktimeEnabled = headersForm.getTransaction().isLocktimeSequenceEnabled();
        locktimeNoneType.setDisable(!headersForm.isEditable() || !locktimeEnabled);
        locktimeBlockType.setDisable(!headersForm.isEditable() || !locktimeEnabled);
        locktimeDateType.setDisable(!headersForm.isEditable() || !locktimeEnabled);
        locktimeBlock.setDisable(!headersForm.isEditable() || !locktimeEnabled);
        locktimeDate.setDisable(!headersForm.isEditable() || !locktimeEnabled);
        locktimeCurrentHeight.setDisable(!headersForm.isEditable() || !locktimeEnabled);

        updateSize();

        Long feeAmt = null;
        if(headersForm.getPsbt() != null) {
            feeAmt = headersForm.getPsbt().getFee();
        } else if(headersForm.getTransaction().getInputs().size() == 1 && headersForm.getTransaction().getInputs().get(0).isCoinBase()) {
            feeAmt = 0L;
        } else if(headersForm.getInputTransactions() != null) {
            feeAmt = calculateFee(headersForm.getInputTransactions());
        } else {
            Wallet wallet = getWalletFromTransactionInputs();
            if(wallet != null) {
                feeAmt = calculateFee(wallet.getTransactions());
            }
        }

        if(feeAmt != null) {
            updateFee(feeAmt);
        }

        transactionDiagram.update(getWalletTransaction(headersForm.getInputTransactions()));

        blockchainForm.managedProperty().bind(blockchainForm.visibleProperty());

        signingWalletForm.managedProperty().bind(signingWalletForm.visibleProperty());
        sigHashForm.managedProperty().bind(sigHashForm.visibleProperty());
        finalizeButtonBox.managedProperty().bind(finalizeButtonBox.visibleProperty());

        signaturesForm.managedProperty().bind(signaturesForm.visibleProperty());
        signButtonBox.managedProperty().bind(signButtonBox.visibleProperty());
        broadcastButtonBox.managedProperty().bind(broadcastButtonBox.visibleProperty());

        signaturesProgressBar.managedProperty().bind(signaturesProgressBar.visibleProperty());
        broadcastProgressBar.managedProperty().bind(broadcastProgressBar.visibleProperty());
        broadcastProgressBar.visibleProperty().bind(signaturesProgressBar.visibleProperty().not());

        broadcastButton.managedProperty().bind(broadcastButton.visibleProperty());
        saveFinalButton.managedProperty().bind(saveFinalButton.visibleProperty());
        saveFinalButton.visibleProperty().bind(broadcastButton.visibleProperty().not());
        broadcastButton.visibleProperty().bind(AppServices.onlineProperty());

        BitcoinURI payjoinURI = getPayjoinURI();
        boolean isPayjoinOriginalTx = payjoinURI != null && headersForm.getPsbt() != null && headersForm.getPsbt().getPsbtInputs().stream().noneMatch(PSBTInput::isFinalized);
        payjoinButton.managedProperty().bind(payjoinButton.visibleProperty());
        payjoinButton.visibleProperty().set(isPayjoinOriginalTx);
        broadcastButton.setDefaultButton(!isPayjoinOriginalTx);

        blockchainForm.setVisible(false);
        signingWalletForm.setVisible(false);
        sigHashForm.setVisible(false);
        finalizeButtonBox.setVisible(false);
        signaturesForm.setVisible(false);
        signButtonBox.setVisible(false);
        broadcastButtonBox.setVisible(false);

        if(headersForm.getBlockTransaction() != null) {
            updateBlockchainForm(headersForm.getBlockTransaction(), AppServices.getCurrentBlockHeight());
        } else if(headersForm.getPsbt() != null) {
            PSBT psbt = headersForm.getPsbt();

            if(headersForm.isEditable()) {
                signingWalletForm.setVisible(true);
                sigHashForm.setVisible(true);
                finalizeButtonBox.setVisible(true);
            } else if(headersForm.getPsbt().isSigned()) {
                signaturesForm.setVisible(true);
                broadcastButtonBox.setVisible(true);
            } else {
                signingWalletForm.setVisible(true);
                finalizeButtonBox.setVisible(true);
                finalizeTransaction.setText("Set Signing Wallet");
            }

            signingWallet.managedProperty().bind(signingWallet.visibleProperty());
            noWalletsWarning.managedProperty().bind(noWalletsWarning.visibleProperty());
            noWalletsWarningLink.managedProperty().bind(noWalletsWarningLink.visibleProperty());
            noWalletsWarningLink.visibleProperty().bind(noWalletsWarning.visibleProperty());

            SigHash psbtSigHash = SigHash.ALL;
            for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
                if(psbtInput.getSigHash() != null) {
                    psbtSigHash = psbtInput.getSigHash();
                }
            }
            sigHash.setValue(psbtSigHash == SigHash.ALL_TAPROOT ? SigHash.ALL : psbtSigHash);
            sigHash.valueProperty().addListener((observable, oldValue, newValue) -> {
                for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
                    psbtInput.setSigHash(psbtInput.isTaproot() && newValue == SigHash.ALL ? SigHash.ALL_TAPROOT : newValue);
                }
            });

            Platform.runLater(this::requestOpenWallets);
        }

        headersForm.signingWalletProperty().addListener((observable, oldValue, signingWallet) -> {
            initializeSignButton(signingWallet);
            updateSignedKeystores(signingWallet);

            int threshold = signingWallet.getDefaultPolicy().getNumSignaturesRequired();
            signaturesProgressBar.initialize(headersForm.getSignatureKeystoreMap(), threshold);
        });

        blockchainForm.setDynamicUpdate(this);
    }

    private void requestOpenWallets() {
        if(id.getScene() != null) {
            EventManager.get().post(new RequestOpenWalletsEvent());
        } else {
            Platform.runLater(this::requestOpenWallets);
        }
    }

    private void updateType() {
        String type = "Legacy";
        if(headersForm.getTransaction().isSegwit() || (headersForm.getPsbt() != null && headersForm.getPsbt().getPsbtInputs().stream().anyMatch(in -> in.getWitnessUtxo() != null))) {
            type = "Segwit";
        }
        segwit.setText(type);
    }

    private void updateSize() {
        size.setText(headersForm.getTransaction().getSize() + " B");
        virtualSize.setText(String.format("%.2f", headersForm.getTransaction().getVirtualSize()) + " vB");
    }

    private Long calculateFee(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        long feeAmt = 0L;
        for(TransactionInput input : headersForm.getTransaction().getInputs()) {
            if(input.isCoinBase()) {
                return 0L;
            }

            BlockTransaction inputTx = inputTransactions.get(input.getOutpoint().getHash());
            if(inputTx == null && headersForm.getInputTransactions() != null) {
                inputTx = headersForm.getInputTransactions().get(input.getOutpoint().getHash());
            }

            if(inputTx == null) {
                if(headersForm.allInputsFetched()) {
                    throw new IllegalStateException("Cannot find transaction for hash " + input.getOutpoint().getHash());
                } else {
                    //Still paging
                    fee.setText("Unknown (" + headersForm.getMaxInputFetched() + " of " + headersForm.getTransaction().getInputs().size() + " inputs fetched)");
                    return null;
                }
            }

            feeAmt += inputTx.getTransaction().getOutputs().get((int)input.getOutpoint().getIndex()).getValue();
        }

        for(TransactionOutput output : headersForm.getTransaction().getOutputs()) {
            feeAmt -= output.getValue();
        }

        return feeAmt;
    }

    private void updateFee(Long feeAmt) {
        fee.setValue(feeAmt);
        double feeRateAmt = feeAmt.doubleValue() / headersForm.getTransaction().getVirtualSize();
        feeRate.setText(String.format("%.2f", feeRateAmt) + " sats/vB" + (headersForm.isTransactionFinalized() ? "" : " (non-final)"));
    }

    private WalletTransaction getWalletTransaction(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        Wallet wallet = getWalletFromTransactionInputs();

        if(wallet != null) {
            Map<Sha256Hash, BlockTransaction> walletInputTransactions = inputTransactions;
            if(walletInputTransactions == null) {
                Set<Sha256Hash> refs = headersForm.getTransaction().getInputs().stream().map(txInput -> txInput.getOutpoint().getHash()).collect(Collectors.toSet());
                walletInputTransactions = new HashMap<>(wallet.getTransactions());
                walletInputTransactions.keySet().retainAll(refs);
            }

            Map<BlockTransactionHashIndex, WalletNode> selectedTxos = new LinkedHashMap<>();
            Map<BlockTransactionHashIndex, WalletNode> walletTxos = wallet.getWalletTxos();
            for(TransactionInput txInput : headersForm.getTransaction().getInputs()) {
                BlockTransactionHashIndex selectedTxo = walletTxos.keySet().stream().filter(txo -> txInput.getOutpoint().getHash().equals(txo.getHash()) && txInput.getOutpoint().getIndex() == txo.getIndex())
                        .findFirst().orElse(getBlockTransactionInput(walletInputTransactions, txInput));
                selectedTxos.put(selectedTxo, walletTxos.get(selectedTxo));
            }

            List<Payment> payments = new ArrayList<>();
            Map<WalletNode, Long> changeMap = new LinkedHashMap<>();
            Map<Script, WalletNode> changeOutputScripts = wallet.getWalletOutputScripts(KeyPurpose.CHANGE);
            for(TransactionOutput txOutput : headersForm.getTransaction().getOutputs()) {
                WalletNode changeNode = changeOutputScripts.get(txOutput.getScript());
                if(changeNode != null) {
                    if(headersForm.getTransaction().getOutputs().size() == 4 && headersForm.getTransaction().getOutputs().stream().anyMatch(txo -> txo != txOutput && txo.getValue() == txOutput.getValue())) {
                        if(selectedTxos.values().stream().allMatch(Objects::nonNull)) {
                            payments.add(new Payment(txOutput.getScript().getToAddress(), ".." + changeNode + " (Fake Mix)", txOutput.getValue(), false, Payment.Type.FAKE_MIX));
                        } else {
                            payments.add(new Payment(txOutput.getScript().getToAddress(), ".." + changeNode + " (Mix)", txOutput.getValue(), false, Payment.Type.MIX));
                        }
                    } else {
                        changeMap.put(changeNode, txOutput.getValue());
                    }
                } else {
                    Payment.Type paymentType = Payment.Type.DEFAULT;
                    Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
                    Wallet premixWallet = masterWallet.getChildWallet(StandardAccount.WHIRLPOOL_PREMIX);
                    if(premixWallet != null && headersForm.getTransaction().getOutputs().stream().anyMatch(premixWallet::isWalletTxo) && txOutput.getIndex() == 1) {
                        paymentType = Payment.Type.WHIRLPOOL_FEE;
                    }

                    BlockTransactionHashIndex receivedTxo = walletTxos.keySet().stream().filter(txo -> txo.getHash().equals(txOutput.getHash()) && txo.getIndex() == txOutput.getIndex()).findFirst().orElse(null);
                    String label = headersForm.getName() == null || (headersForm.getName().startsWith("[") && headersForm.getName().endsWith("]") && headersForm.getName().length() == 8) ? null : headersForm.getName();
                    try {
                        Payment payment = new Payment(txOutput.getScript().getToAddresses()[0], receivedTxo != null ? receivedTxo.getLabel() : label, txOutput.getValue(), false, paymentType);
                        WalletTransaction createdTx = AppServices.get().getCreatedTransaction(selectedTxos.keySet());
                        if(createdTx != null) {
                            Optional<String> optLabel = createdTx.getPayments().stream().filter(pymt -> pymt.getAddress().equals(payment.getAddress()) && pymt.getAmount() == payment.getAmount()).map(Payment::getLabel).findFirst();
                            if(optLabel.isPresent()) {
                                payment.setLabel(optLabel.get());
                                outputIndexLabels.put(txOutput.getIndex(), optLabel.get());
                            }
                        }
                        payments.add(payment);
                    } catch(Exception e) {
                        //ignore
                    }
                }
            }

            return new WalletTransaction(wallet, headersForm.getTransaction(), Collections.emptyList(), List.of(selectedTxos), payments, changeMap, fee.getValue(), walletInputTransactions);
        } else {
            Map<BlockTransactionHashIndex, WalletNode> selectedTxos = headersForm.getTransaction().getInputs().stream()
                    .collect(Collectors.toMap(txInput -> getBlockTransactionInput(inputTransactions, txInput),
                            txInput -> new WalletNode("m/0"),
                            (u, v) -> { throw new IllegalStateException("Duplicate TXOs"); },
                            LinkedHashMap::new));
            selectedTxos.entrySet().forEach(entry -> entry.setValue(null));

            List<Payment> payments = new ArrayList<>();
            for(TransactionOutput txOutput : headersForm.getTransaction().getOutputs()) {
                try {
                    payments.add(new Payment(txOutput.getScript().getToAddresses()[0], null, txOutput.getValue(), false));
                } catch(Exception e) {
                    //ignore
                }
            }

            return new WalletTransaction(null, headersForm.getTransaction(), Collections.emptyList(), List.of(selectedTxos), payments, Collections.emptyMap(), fee.getValue(), inputTransactions);
        }
    }

    private BlockTransactionHashIndex getBlockTransactionInput(Map<Sha256Hash, BlockTransaction> inputTransactions, TransactionInput txInput) {
        if(inputTransactions != null) {
            BlockTransaction blockTransaction = inputTransactions.get(txInput.getOutpoint().getHash());
            if(blockTransaction != null) {
                TransactionOutput txOutput = blockTransaction.getTransaction().getOutputs().get((int) txInput.getOutpoint().getIndex());
                return new BlockTransactionHashIndex(blockTransaction.getHash(), blockTransaction.getHeight(), blockTransaction.getDate(), blockTransaction.getFee(), txInput.getOutpoint().getIndex(), txOutput.getValue());
            }
        }

        return new BlockTransactionHashIndex(txInput.getOutpoint().getHash(), 0, null, null, txInput.getOutpoint().getIndex(), 0);
    }

    private Wallet getWalletFromTransactionInputs() {
        for(TransactionInput txInput : headersForm.getTransaction().getInputs()) {
            for(Wallet openWallet : AppServices.get().getOpenWallets().keySet()) {
                if(openWallet.isWalletTxo(txInput)) {
                    return openWallet;
                }
            }
        }

        return null;
    }

    private void updateBlockchainForm(BlockTransaction blockTransaction, Integer currentHeight) {
        signaturesForm.setVisible(false);
        blockchainForm.setVisible(true);

        if(Sha256Hash.ZERO_HASH.equals(blockTransaction.getBlockHash()) && blockTransaction.getHeight() == 0 && headersForm.getSigningWallet() == null) {
            //A zero block hash indicates that this blocktransaction is incomplete and the height is likely incorrect if we are not sending a tx
            blockStatus.setText("Unknown");
        } else if(currentHeight == null) {
            blockStatus.setText(blockTransaction.getHeight() > 0 ? "Confirmed" : "Unconfirmed");
        } else {
            int confirmations = blockTransaction.getHeight() > 0 ? currentHeight - blockTransaction.getHeight() + 1 : 0;
            if(confirmations == 0) {
                blockStatus.setText("Unconfirmed");
            } else if(confirmations == 1) {
                blockStatus.setText(confirmations + " Confirmation");
            } else {
                blockStatus.setText(confirmations + " Confirmations");
            }

            if(confirmations <= BlockTransactionHash.BLOCKS_TO_CONFIRM) {
                ConfirmationProgressIndicator indicator;
                if(blockStatus.getGraphic() == null) {
                    indicator = new ConfirmationProgressIndicator(confirmations);
                    blockStatus.setGraphic(indicator);
                } else {
                    indicator = (ConfirmationProgressIndicator)blockStatus.getGraphic();
                    indicator.setConfirmations(confirmations);
                }
            } else {
                blockStatus.setGraphic(null);
            }
        }

        blockHeightField.managedProperty().bind(blockHeightField.visibleProperty());
        blockTimestampField.managedProperty().bind(blockTimestampField.visibleProperty());
        blockHashField.managedProperty().bind(blockHashField.visibleProperty());

        if(blockTransaction.getHeight() > 0) {
            blockHeightField.setVisible(true);
            blockHeight.setText(Integer.toString(blockTransaction.getHeight()));
        } else {
            blockHeightField.setVisible(false);
        }

        if(blockTransaction.getDate() != null) {
            blockTimestampField.setVisible(true);
            SimpleDateFormat dateFormat = new SimpleDateFormat(BLOCK_TIMESTAMP_DATE_FORMAT);
            blockTimestamp.setText(dateFormat.format(blockTransaction.getDate()));
        } else {
            blockTimestampField.setVisible(false);
        }

        if(blockTransaction.getBlockHash() != null && !blockTransaction.getBlockHash().equals(Sha256Hash.ZERO_HASH)) {
            blockHashField.setVisible(true);
            blockHash.setText(blockTransaction.getBlockHash().toString());
            blockHash.setContextMenu(new BlockHeightContextMenu(blockTransaction.getBlockHash()));
        } else {
            blockHashField.setVisible(false);
        }
    }

    private void initializeSignButton(Wallet signingWallet) {
        Optional<Keystore> softwareKeystore = signingWallet.getKeystores().stream().filter(keystore -> keystore.getSource().equals(KeystoreSource.SW_SEED)).findAny();
        Optional<Keystore> usbKeystore = signingWallet.getKeystores().stream().filter(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB)).findAny();
        if(softwareKeystore.isEmpty() && usbKeystore.isEmpty()) {
            signButton.setDisable(true);
        } else if(softwareKeystore.isEmpty()) {
            Glyph usbGlyph = new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
            usbGlyph.setFontSize(20);
            signButton.setGraphic(usbGlyph);
        }
    }

    private BitcoinURI getPayjoinURI() {
        if(headersForm.getPsbt() != null) {
            for(TransactionOutput txOutput : headersForm.getPsbt().getTransaction().getOutputs()) {
                try {
                    Address address = txOutput.getScript().getToAddresses()[0];
                    BitcoinURI bitcoinURI = AppServices.getPayjoinURI(address);
                    if(bitcoinURI != null) {
                        return bitcoinURI;
                    }
                } catch(Exception e) {
                    //ignore
                }
            }
        }

        return null;
    }

    private static class BlockHeightContextMenu extends ContextMenu {
        public BlockHeightContextMenu(Sha256Hash blockHash) {
            MenuItem copyBlockHash = new MenuItem("Copy Block Hash");
            copyBlockHash.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(blockHash.toString());
                Clipboard.getSystemClipboard().setContent(content);
            });
            getItems().add(copyBlockHash);
        }
    }

    private void updateTxId() {
        id.setText(headersForm.getTransaction().calculateTxId(false).toString());
        if(!headersForm.isTransactionFinalized()) {
            addStyleClass(id, UNFINALIZED_TXID_CLASS);
            addStyleClass(size, UNFINALIZED_TXID_CLASS);
            addStyleClass(virtualSize, UNFINALIZED_TXID_CLASS);
            addStyleClass(feeRate, UNFINALIZED_TXID_CLASS);
        } else {
            id.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
            size.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
            virtualSize.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
            feeRate.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
        }
    }

    private void addStyleClass(Node node, String styleClass) {
        if(!node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    public void copyId(ActionEvent event) {
        ClipboardContent content = new ClipboardContent();
        content.putString(headersForm.getTransaction().getTxId().toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    public void setLocktimeToCurrentHeight(ActionEvent event) {
        if(AppServices.getCurrentBlockHeight() != null && locktimeBlock.isEditable()) {
            locktimeBlock.getValueFactory().setValue(AppServices.getCurrentBlockHeight());
            Platform.runLater(() -> locktimeBlockType.requestFocus());
        }
    }

    public void openWallet(ActionEvent event) {
        EventManager.get().post(new RequestWalletOpenEvent(noWalletsWarningLink.getScene().getWindow()));
    }

    public void finalizeTransaction(ActionEvent event) {
        EventManager.get().post(new FinalizeTransactionEvent(headersForm.getPsbt(), signingWallet.getValue()));
    }

    public void showPSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);

        //TODO: Remove once Cobo Vault has upgraded to UR2.0
        boolean addLegacyEncodingOption = headersForm.getSigningWallet().getKeystores().stream().anyMatch(keystore -> keystore.getWalletModel().equals(WalletModel.COBO_VAULT) || keystore.getWalletModel().equals(WalletModel.SPARROW));

        CryptoPSBT cryptoPSBT = new CryptoPSBT(headersForm.getPsbt().serialize());
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(cryptoPSBT.toUR(), addLegacyEncodingOption);
        qrDisplayDialog.show();
    }

    public void scanPSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);

        EventManager.get().post(new RequestQRScanEvent(toggleButton.getScene().getWindow()));
    }

    public void savePSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);

        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PSBT");

        if(headersForm.getName() != null && !headersForm.getName().isEmpty()) {
            fileChooser.setInitialFileName(headersForm.getName() + ".psbt");
        }

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            if(!file.getName().toLowerCase().endsWith(".psbt")) {
                file = new File(file.getAbsolutePath() + ".psbt");
            }

            try(FileOutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(headersForm.getPsbt().serialize());
            } catch(IOException e) {
                log.error("Error saving PSBT", e);
                AppServices.showErrorDialog("Error saving PSBT", "Cannot write to " + file.getAbsolutePath());
            }
        }
    }

    public void loadPSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);

        EventManager.get().post(new RequestTransactionOpenEvent(toggleButton.getScene().getWindow()));
    }

    public void signPSBT(ActionEvent event) {
        signSoftwareKeystores();
        signUsbKeystores();
    }

    private void signSoftwareKeystores() {
        if(headersForm.getSigningWallet().getKeystores().stream().noneMatch(Keystore::hasPrivateKey)) {
            return;
        }

        if(headersForm.getPsbt().isSigned()) {
            return;
        }

        Wallet copy = headersForm.getSigningWallet().copy();
        String walletId = headersForm.getAvailableWallets().get(headersForm.getSigningWallet()).getWalletId(headersForm.getSigningWallet());

        if(copy.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(copy.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    signUnencryptedKeystores(decryptedWallet);
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                    AppServices.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                decryptWalletService.start();
            }
        } else {
            signUnencryptedKeystores(copy);
        }
    }

    private void signUnencryptedKeystores(Wallet unencryptedWallet) {
        try {
            unencryptedWallet.sign(headersForm.getPsbt());
            updateSignedKeystores(headersForm.getSigningWallet());
        } catch(Exception e) {
            log.warn("Failed to Sign", e);
            AppServices.showErrorDialog("Failed to Sign", e.getMessage());
        }
    }

    private void signUsbKeystores() {
        if(headersForm.getPsbt().isSigned()) {
            return;
        }

        List<String> fingerprints = headersForm.getSigningWallet().getKeystores().stream().map(keystore -> keystore.getKeyDerivation().getMasterFingerprint()).collect(Collectors.toList());
        List<Device> signingDevices = AppServices.getDevices().stream().filter(device -> fingerprints.contains(device.getFingerprint())).collect(Collectors.toList());
        if(signingDevices.isEmpty() && headersForm.getSigningWallet().getKeystores().stream().noneMatch(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB))) {
            return;
        }

        DeviceSignDialog dlg = new DeviceSignDialog(fingerprints, headersForm.getPsbt());
        dlg.initModality(Modality.NONE);
        Stage stage = (Stage)dlg.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        Optional<PSBT> optionalSignedPsbt = dlg.showAndWait();
        if(optionalSignedPsbt.isPresent()) {
            PSBT signedPsbt = optionalSignedPsbt.get();
            headersForm.getPsbt().combine(signedPsbt);
            EventManager.get().post(new PSBTCombinedEvent(headersForm.getPsbt()));
        }
    }

    private void updateSignedKeystores(Wallet signingWallet) {
        Map<?, Map<TransactionSignature, Keystore>> signedKeystoresMap = headersForm.getPsbt() == null ? signingWallet.getSignedKeystores(headersForm.getTransaction()) : signingWallet.getSignedKeystores(headersForm.getPsbt());
        Optional<Map<TransactionSignature, Keystore>> optSignedKeystores = signedKeystoresMap.values().stream().filter(map -> !map.isEmpty()).min(Comparator.comparingInt(Map::size));
        optSignedKeystores.ifPresent(signedKeystores -> {
            headersForm.getSignatureKeystoreMap().keySet().retainAll(signedKeystores.keySet());
            headersForm.getSignatureKeystoreMap().putAll(signedKeystores);
        });
    }

    private void finalizePSBT() {
        if(headersForm.getPsbt() != null && headersForm.getPsbt().isSigned() && !headersForm.getPsbt().isFinalized()) {
            headersForm.getSigningWallet().finalise(headersForm.getPsbt());
            EventManager.get().post(new PSBTFinalizedEvent(headersForm.getPsbt()));
        }
    }

    public void extractTransaction(ActionEvent event) {
        viewFinalButton.setDisable(true);

        Transaction finalTx = headersForm.getPsbt().extractTransaction();
        headersForm.setFinalTransaction(finalTx);
        EventManager.get().post(new TransactionExtractedEvent(headersForm.getPsbt(), finalTx));
    }

    public void broadcastTransaction(ActionEvent event) {
        broadcastButton.setDisable(true);
        if(headersForm.getPsbt() != null) {
            extractTransaction(event);
        }

        if(headersForm.getSigningWallet() instanceof FinalizingPSBTWallet) {
            //Ensure the script hashes of the UTXOs in FinalizingPSBTWallet are subscribed to
            ElectrumServer.TransactionHistoryService historyService = new ElectrumServer.TransactionHistoryService(headersForm.getSigningWallet());
            historyService.setOnFailed(workerStateEvent -> {
                log.error("Error subscribing FinalizingPSBTWallet script hashes", workerStateEvent.getSource().getException());
            });
            historyService.start();
        }

        ElectrumServer.BroadcastTransactionService broadcastTransactionService = new ElectrumServer.BroadcastTransactionService(headersForm.getTransaction());
        broadcastTransactionService.setOnSucceeded(workerStateEvent -> {
            //Although we wait for WalletNodeHistoryChangedEvent to indicate tx is in mempool, start a scheduled service to check the script hashes should notifications fail
            if(headersForm.getSigningWallet() != null) {
                if(transactionMempoolService != null) {
                    transactionMempoolService.cancel();
                }

                transactionMempoolService = new ElectrumServer.TransactionMempoolService(headersForm.getSigningWallet(), headersForm.getTransaction().getTxId(), headersForm.getSigningWalletNodes());
                transactionMempoolService.setDelay(Duration.seconds(3));
                transactionMempoolService.setPeriod(Duration.seconds(10));
                transactionMempoolService.setRestartOnFailure(false);
                transactionMempoolService.setOnSucceeded(mempoolWorkerStateEvent -> {
                    Set<String> scriptHashes = transactionMempoolService.getValue();
                    if(!scriptHashes.isEmpty()) {
                        Platform.runLater(() -> EventManager.get().post(new WalletNodeHistoryChangedEvent(scriptHashes.iterator().next())));
                    }

                    if(transactionMempoolService.getIterationCount() > 3) {
                        transactionMempoolService.cancel();
                        broadcastProgressBar.setProgress(0);
                        log.error("Timeout searching for broadcasted transaction");
                        AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not register it in the mempool. It is safe to try broadcasting again.");
                        broadcastButton.setDisable(false);
                    }
                });
                transactionMempoolService.setOnFailed(mempoolWorkerStateEvent -> {
                    transactionMempoolService.cancel();
                    broadcastProgressBar.setProgress(0);
                    log.error("Timeout searching for broadcasted transaction");
                    AppServices.showErrorDialog("Timeout searching for broadcasted transaction", "The transaction was broadcast but the server did not indicate it had entered the mempool. It is safe to try broadcasting again.");
                    broadcastButton.setDisable(false);
                });
                transactionMempoolService.start();
            } else {
                Sha256Hash txid = headersForm.getTransaction().getTxId();
                ElectrumServer.TransactionReferenceService transactionReferenceService = new ElectrumServer.TransactionReferenceService(Set.of(txid));
                transactionReferenceService.setOnSucceeded(successEvent -> {
                    Map<Sha256Hash, BlockTransaction> transactionMap = transactionReferenceService.getValue();
                    BlockTransaction blockTransaction = transactionMap.get(txid);
                    if(blockTransaction != null) {
                        headersForm.setBlockTransaction(blockTransaction);
                        updateBlockchainForm(blockTransaction, AppServices.getCurrentBlockHeight());
                    }
                    EventManager.get().post(new TransactionReferencesFinishedEvent(headersForm.getTransaction(), blockTransaction));
                });
                transactionReferenceService.setOnFailed(failedEvent -> {
                    log.error("Error fetching broadcasted transaction", failedEvent.getSource().getException());
                    EventManager.get().post(new TransactionReferencesFailedEvent(headersForm.getTransaction(), failedEvent.getSource().getException()));
                });
                EventManager.get().post(new TransactionReferencesStartedEvent(headersForm.getTransaction()));
                transactionReferenceService.start();
            }
        });
        broadcastTransactionService.setOnFailed(workerStateEvent -> {
            broadcastProgressBar.setProgress(0);
            log.error("Error broadcasting transaction", workerStateEvent.getSource().getException());
            AppServices.showErrorDialog("Error broadcasting transaction", "The server returned an error when broadcasting the transaction. The server response is contained in the log (See Help > Show Log File).");
            broadcastButton.setDisable(false);
        });

        signaturesProgressBar.setVisible(false);
        broadcastProgressBar.setProgress(-1);
        broadcastTransactionService.start();
    }

    public void saveFinalTransaction(ActionEvent event) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Final Transaction");

        if(headersForm.getName() != null && !headersForm.getName().isEmpty()) {
            fileChooser.setInitialFileName(headersForm.getName().replace(".psbt", "") + ".txn");
        }

        AppServices.moveToActiveWindowScreen(window, 800, 450);
        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try {
                try(PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    Transaction finalTx = headersForm.getPsbt().extractTransaction();
                    writer.print(Utils.bytesToHex(finalTx.bitcoinSerialize()));
                }
            } catch(IOException e) {
                log.error("Error saving transaction", e);
                AppServices.showErrorDialog("Error saving transaction", "Cannot write to " + file.getAbsolutePath());
            }
        }
    }

    public void getPayjoinTransaction(ActionEvent event) {
        BitcoinURI payjoinURI = getPayjoinURI();
        if(payjoinURI == null) {
            throw new IllegalStateException("No valid Payjoin URI");
        }

        Payjoin payjoin = new Payjoin(payjoinURI, headersForm.getSigningWallet(), headersForm.getPsbt());
        Payjoin.RequestPayjoinPSBTService requestPayjoinPSBTService = new Payjoin.RequestPayjoinPSBTService(payjoin, true);
        requestPayjoinPSBTService.setOnSucceeded(successEvent -> {
            PSBT proposalPsbt = requestPayjoinPSBTService.getValue();
            EventManager.get().post(new ViewPSBTEvent(payjoinButton.getScene().getWindow(), headersForm.getName() + " Payjoin", null, proposalPsbt));
        });
        requestPayjoinPSBTService.setOnFailed(failedEvent -> {
            AppServices.showErrorDialog("Error Requesting Payjoin Transaction", failedEvent.getSource().getException().getMessage());
        });
        requestPayjoinPSBTService.start();
    }

    @Override
    public void update() {
        BlockTransaction blockTransaction = headersForm.getBlockTransaction();
        Sha256Hash txId = headersForm.getTransaction().getTxId();
        if(headersForm.getSigningWallet() != null && headersForm.getSigningWallet().getTransactions().containsKey(txId)) {
            blockTransaction = headersForm.getSigningWallet().getTransactions().get(txId);
        }

        if(blockTransaction != null && AppServices.getCurrentBlockHeight() != null) {
            updateBlockchainForm(blockTransaction, AppServices.getCurrentBlockHeight());
        }
    }

    @Subscribe
    public void transactionChanged(TransactionChangedEvent event) {
        if(headersForm.getTransaction().equals(event.getTransaction())) {
            updateTxId();
            boolean locktimeEnabled = headersForm.getTransaction().isLocktimeSequenceEnabled();
            locktimeNoneType.setDisable(!locktimeEnabled);
            locktimeBlockType.setDisable(!locktimeEnabled);
            locktimeBlock.setDisable(!locktimeEnabled);
            locktimeDateType.setDisable(!locktimeEnabled);
            locktimeDate.setDisable(!locktimeEnabled);
            locktimeCurrentHeight.setDisable(!locktimeEnabled);
        }
    }

    @Subscribe
    public void blockTransactionFetched(BlockTransactionFetchedEvent event) {
        if(event.getTxId().equals(headersForm.getTransaction().getTxId())) {
            if(event.getBlockTransaction() != null && (!Sha256Hash.ZERO_HASH.equals(event.getBlockTransaction().getBlockHash()) || headersForm.getBlockTransaction() == null)) {
                updateBlockchainForm(event.getBlockTransaction(), AppServices.getCurrentBlockHeight());
            } else if(headersForm.getPsbt() == null && headersForm.getBlockTransaction() == null) {
                boolean isSigned = true;
                ObservableMap<TransactionSignature, Keystore> signatureKeystoreMap = FXCollections.observableMap(new LinkedHashMap<>());
                for(TransactionInput txInput : headersForm.getTransaction().getInputs()) {
                    List<TransactionSignature> signatures = txInput.hasWitness() ? txInput.getWitness().getSignatures() : txInput.getScriptSig().getSignatures();

                    if(signatures.isEmpty()) {
                        isSigned = false;
                        break;
                    }

                    if(signatureKeystoreMap.isEmpty()) {
                        for(int i = 0; i < signatures.size(); i++) {
                            signatureKeystoreMap.put(signatures.get(i), new Keystore("Keystore " + (i+1)));
                        }
                    }
                }

                if(isSigned) {
                    blockchainForm.setVisible(false);
                    signaturesForm.setVisible(true);
                    broadcastButtonBox.setVisible(true);
                    viewFinalButton.setDisable(true);

                    if(headersForm.getSigningWallet() == null) {
                        for(Wallet wallet : AppServices.get().getOpenWallets().keySet()) {
                            if(wallet.canSign(headersForm.getTransaction())) {
                                headersForm.setSigningWallet(wallet);
                                break;
                            }
                        }
                    }

                    if(headersForm.getSigningWallet() == null) {
                        signaturesProgressBar.initialize(signatureKeystoreMap, signatureKeystoreMap.size());
                    }
                }
            }

            Long feeAmt = calculateFee(event.getInputTransactions());
            if(feeAmt != null) {
                updateFee(feeAmt);
            }

            Map<Sha256Hash, BlockTransaction> allFetchedInputTransactions = new HashMap<>(event.getInputTransactions());
            if(headersForm.getInputTransactions() != null) {
                allFetchedInputTransactions.putAll(headersForm.getInputTransactions());
            }
            transactionDiagram.update(getWalletTransaction(allFetchedInputTransactions));
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        fee.refresh(event.getBitcoinUnit());
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        if(id.getScene().getWindow().equals(event.getWindow()) && headersForm.getPsbt() != null && headersForm.getBlockTransaction() == null) {
            List<Wallet> availableWallets = event.getWallets().stream().filter(wallet -> wallet.canSign(headersForm.getPsbt())).sorted(new WalletSignComparator()).collect(Collectors.toList());
            Map<Wallet, Storage> availableWalletsMap = new LinkedHashMap<>(event.getWalletsMap());
            availableWalletsMap.keySet().retainAll(availableWallets);
            headersForm.getAvailableWallets().keySet().retainAll(availableWallets);
            headersForm.getAvailableWallets().putAll(availableWalletsMap);
            signingWallet.setItems(FXCollections.observableList(availableWallets));

            if(!availableWallets.isEmpty()) {
                if(!headersForm.isEditable() && (availableWallets.size() == 1 || headersForm.getPsbt().isSigned())) {
                    signingWalletForm.setVisible(false);
                    sigHashForm.setVisible(false);
                    finalizeButtonBox.setVisible(false);

                    signaturesForm.setVisible(true);
                    headersForm.setSigningWallet(availableWallets.get(0));
                    signButton.setDisable(false);

                    if(headersForm.getPsbt().isSigned()) {
                        finalizePSBT();
                        broadcastButtonBox.setVisible(true);
                    } else {
                        signButtonBox.setVisible(true);
                    }
                } else {
                    if(availableWallets.contains(headersForm.getSigningWallet())) {
                        signingWallet.setValue(headersForm.getSigningWallet());
                    } else {
                        signingWallet.setValue(availableWallets.get(0));
                    }
                    noWalletsWarning.setVisible(false);
                    signingWallet.setVisible(true);
                    finalizeTransaction.setDisable(false);
                    signButton.setDisable(false);
                }
            } else {
                if(headersForm.getPsbt().isSigned()) {
                    if(headersForm.getSigningWallet() == null) {
                        //As no signing wallet is available, but we want to show the PSBT has been signed and automatically finalize it, construct a special wallet with default named keystores
                        Wallet signedWallet = new FinalizingPSBTWallet(headersForm.getPsbt());
                        headersForm.setSigningWallet(signedWallet);
                    }

                    //Finalize this PSBT if necessary as fully signed PSBTs are automatically finalized on once the signature threshold has been reached
                    finalizePSBT();
                    broadcastButtonBox.setVisible(true);
                } else {
                    noWalletsWarning.setVisible(true);
                    signingWallet.setVisible(false);
                    finalizeTransaction.setDisable(true);
                    signButton.setDisable(true);
                }
            }
        }
    }

    @Subscribe
    public void finalizeTransaction(FinalizeTransactionEvent event) {
        if(headersForm.getPsbt() == event.getPsbt()) {
            version.setDisable(true);
            locktimeNoneType.setDisable(true);
            locktimeBlockType.setDisable(true);
            locktimeDateType.setDisable(true);
            locktimeBlock.setDisable(true);
            locktimeDate.setDisable(true);
            locktimeCurrentHeight.setVisible(false);
            updateTxId();

            headersForm.setSigningWallet(event.getSigningWallet());

            signingWalletForm.setVisible(false);
            sigHashForm.setVisible(false);
            finalizeButtonBox.setVisible(false);
            signaturesForm.setVisible(true);

            if(event.getPsbt().isSigned()) {
                broadcastButtonBox.setVisible(true);
            } else {
                signButtonBox.setVisible(true);
            }
        }
    }

    @Subscribe
    public void psbtCombined(PSBTCombinedEvent event) {
        if(event.getPsbt().equals(headersForm.getPsbt())) {
            if(headersForm.getSigningWallet() != null) {
                updateSignedKeystores(headersForm.getSigningWallet());
            } else if(headersForm.getPsbt().isSigned()) {
                Wallet signedWallet = new FinalizingPSBTWallet(headersForm.getPsbt());
                headersForm.setSigningWallet(signedWallet);
                finalizePSBT();
                EventManager.get().post(new FinalizeTransactionEvent(headersForm.getPsbt(), signedWallet));
            }
        }
    }

    @Subscribe
    public void psbtFinalized(PSBTFinalizedEvent event) {
        if(event.getPsbt().equals(headersForm.getPsbt())) {
            if(headersForm.getSigningWallet() != null) {
                updateSignedKeystores(headersForm.getSigningWallet());
            }

            signButtonBox.setVisible(false);
            broadcastButtonBox.setVisible(true);
        }
    }

    @Subscribe
    public void keystoreSigned(KeystoreSignedEvent event) {
        if(headersForm.getSignedKeystores().contains(event.getKeystore()) && headersForm.getPsbt() != null) {
            //Attempt to finalize PSBT - will do nothing if all inputs are not signed
            finalizePSBT();
        }
    }

    @Subscribe
    public void transactionExtracted(TransactionExtractedEvent event) {
        if(event.getPsbt().equals(headersForm.getPsbt())) {
            updateTxId();
            updateType();
            updateSize();
            updateFee(headersForm.getPsbt().getFee());
            transactionDiagram.update(getWalletTransaction(headersForm.getInputTransactions()));
        }
    }

    @Subscribe
    public void walletNodeHistoryChanged(WalletNodeHistoryChangedEvent event) {
        if(headersForm.getSigningWallet() != null && event.getWalletNode(headersForm.getSigningWallet()) != null && headersForm.isTransactionFinalized()) {
            if(transactionMempoolService != null) {
                transactionMempoolService.cancel();
            }

            Sha256Hash txid = headersForm.getTransaction().getTxId();
            ElectrumServer.TransactionReferenceService transactionReferenceService = new ElectrumServer.TransactionReferenceService(Set.of(txid), event.getScriptHash());
            transactionReferenceService.setOnSucceeded(successEvent -> {
                Map<Sha256Hash, BlockTransaction> transactionMap = transactionReferenceService.getValue();
                BlockTransaction blockTransaction = transactionMap.get(txid);
                if(blockTransaction != null) {
                    headersForm.setBlockTransaction(blockTransaction);
                    updateBlockchainForm(blockTransaction, AppServices.getCurrentBlockHeight());
                }
                EventManager.get().post(new TransactionReferencesFinishedEvent(headersForm.getTransaction(), blockTransaction));
            });
            transactionReferenceService.setOnFailed(failEvent -> {
                log.error("Could not update block transaction", failEvent.getSource().getException());
                EventManager.get().post(new TransactionReferencesFailedEvent(headersForm.getTransaction(), failEvent.getSource().getException()));
            });
            EventManager.get().post(new TransactionReferencesStartedEvent(headersForm.getTransaction()));
            transactionReferenceService.start();
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        //Update tx and input/output reference labels on history changed wallet if this txid matches and label is null
        if(headersForm.getSigningWallet() != null && !(headersForm.getSigningWallet() instanceof FinalizingPSBTWallet)) {
            Sha256Hash txid = headersForm.getTransaction().getTxId();

            List<Entry> changedLabelEntries = new ArrayList<>();
            BlockTransaction blockTransaction = event.getWallet().getTransactions().get(txid);
            if(blockTransaction != null && blockTransaction.getLabel() == null) {
                blockTransaction.setLabel(headersForm.getName());
                changedLabelEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
            }

            for(WalletNode walletNode : event.getHistoryChangedNodes()) {
                for(BlockTransactionHashIndex output : walletNode.getTransactionOutputs()) {
                    if(output.getHash().equals(txid) && output.getLabel() == null) { //If we send to ourselves, usually change
                        String label = outputIndexLabels.containsKey((int)output.getIndex()) ? outputIndexLabels.get((int)output.getIndex()) : headersForm.getName();
                        output.setLabel(label + (walletNode.getKeyPurpose() == KeyPurpose.CHANGE ? " (change)" : " (received)"));
                        changedLabelEntries.add(new HashIndexEntry(event.getWallet(), output, HashIndexEntry.Type.OUTPUT, walletNode.getKeyPurpose()));
                    }
                    if(output.getSpentBy() != null && output.getSpentBy().getHash().equals(txid) && output.getSpentBy().getLabel() == null) { //The norm - sending out
                        output.getSpentBy().setLabel(headersForm.getName() + " (input)");
                        changedLabelEntries.add(new HashIndexEntry(event.getWallet(), output.getSpentBy(), HashIndexEntry.Type.INPUT, walletNode.getKeyPurpose()));
                    }
                }
            }

            if(!changedLabelEntries.isEmpty()) {
                Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(event.getWallet(), changedLabelEntries)));
            }
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        if(headersForm.getBlockTransaction() != null) {
            updateBlockchainForm(headersForm.getBlockTransaction(), event.getHeight());
        }
        if(futureBlockWarning.isVisible()) {
            futureBlockWarning.setVisible(AppServices.getCurrentBlockHeight() != null && locktimeBlock.getValue() > event.getHeight());
        }
    }

    private static class WalletSignComparator implements Comparator<Wallet> {
        private static final List<KeystoreSource> sourceOrder = List.of(KeystoreSource.SW_WATCH, KeystoreSource.HW_AIRGAPPED, KeystoreSource.HW_USB, KeystoreSource.SW_SEED);

        @Override
        public int compare(Wallet wallet1, Wallet wallet2) {
            return getHighestSourceIndex(wallet2) - getHighestSourceIndex(wallet1);

        }

        private int getHighestSourceIndex(Wallet wallet) {
            return wallet.getKeystores().stream().map(keystore -> sourceOrder.indexOf(keystore.getSource())).mapToInt(v -> v).max().orElse(0);
        }
    }
}