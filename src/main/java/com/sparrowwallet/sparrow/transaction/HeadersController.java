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
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.bbqr.BBQR;
import com.sparrowwallet.sparrow.io.bbqr.BBQRType;
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

import javax.swing.text.html.Option;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class HeadersController extends TransactionFormController implements Initializable, DynamicUpdate {
    private static final Logger log = LoggerFactory.getLogger(HeadersController.class);
    public static final String LOCKTIME_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String BLOCK_TIMESTAMP_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss ZZZ";
    public static final String UNFINALIZED_TXID_CLASS = "unfinalized-txid";

    public static final String MAX_LOCKTIME_DATE = "2106-02-07T06:28:15Z";
    public static final String MIN_LOCKTIME_DATE = "1985-11-05T00:53:20Z";

    private static final Pattern MIN_MEMPOOL_FEE = Pattern.compile("the transaction was rejected by network rules.*mempool min fee not met, (\\d+) < (\\d+).*", Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern RBF_INSUFFICIENT_FEE = Pattern.compile("insufficient fee, rejecting replacement.*?(\\d+\\.?\\d*) < (\\d+\\.?\\d*)");
    private static final Pattern RBF_INSUFFICIENT_FEE_RATE = Pattern.compile("insufficient fee, rejecting replacement.*new feerate (\\d+\\.?\\d*)[^\\d]*(\\d+\\.?\\d*)[^\\d]*");

    private HeadersForm headersForm;

    @FXML
    private IdLabel id;

    @FXML
    private ToggleButton copyTxid;

    @FXML
    private ToggleButton openBlockExplorer;

    @FXML
    private TransactionDiagram transactionDiagram;

    @FXML
    private TransactionDiagramLabel transactionDiagramLabel;

    @FXML
    private IntegerSpinner version;

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
    private IntegerSpinner locktimeNone;

    @FXML
    private IntegerSpinner locktimeBlock;

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

    private final Map<Integer, String> outputIndexLabels = new TreeMap<>();

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

        version.setValueFactory(new IntegerSpinner.ValueFactory(1, 2, (int)tx.getVersion()));
        version.valueProperty().addListener((obs, oldValue, newValue) -> {
            if(newValue == null || newValue < 1 || newValue > 2) {
                return;
            }

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

        locktimeNone.setValueFactory(new IntegerSpinner.ValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1, 0));
        if(tx.getLocktime() < Transaction.MAX_BLOCK_LOCKTIME) {
            locktimeBlock.setValueFactory(new IntegerSpinner.ValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1, (int)tx.getLocktime()));
            if(tx.getLocktime() == 0) {
                locktimeToggleGroup.selectToggle(locktimeNoneType);
            } else {
                locktimeToggleGroup.selectToggle(locktimeBlockType);
            }
            LocalDateTime date = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDate.setDateTimeValue(date);
        } else {
            locktimeBlock.setValueFactory(new IntegerSpinner.ValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1));
            LocalDateTime date = Instant.ofEpochSecond(tx.getLocktime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeDate.setDateTimeValue(date);
            locktimeToggleGroup.selectToggle(locktimeDateType);
        }

        locktimeBlock.valueProperty().addListener((obs, oldValue, newValue) -> {
            if(newValue == null || newValue < 0 || newValue >= Transaction.MAX_BLOCK_LOCKTIME) {
                return;
            }

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
                feeAmt = calculateFee(wallet.getWalletTransactions());
            }
        }

        if(feeAmt != null) {
            updateFee(feeAmt);
        }

        headersForm.walletTransactionProperty().addListener((observable, oldValue, walletTransaction) -> {
            transactionDiagram.update(walletTransaction);
        });
        transactionDiagram.labelProperty().set(transactionDiagramLabel);
        headersForm.setWalletTransaction(getWalletTransaction(headersForm.getInputTransactions()));

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

            boolean taprootInput = psbt.getPsbtInputs().stream().anyMatch(PSBTInput::isTaproot);
            SigHash psbtSigHash = psbt.getPsbtInputs().stream().map(PSBTInput::getSigHash).filter(Objects::nonNull).findFirst().orElse(taprootInput ? SigHash.DEFAULT : SigHash.ALL);
            sigHash.setItems(FXCollections.observableList(taprootInput ? SigHash.TAPROOT_SIGNING_TYPES : SigHash.LEGACY_SIGNING_TYPES));
            sigHash.setValue(psbtSigHash);
            sigHash.setConverter(new StringConverter<>() {
                @Override
                public String toString(SigHash sigHash) {
                    if(sigHash == null) {
                        return "";
                    }

                    return sigHash.getName() + ((taprootInput && sigHash == SigHash.DEFAULT) || (!taprootInput && sigHash == SigHash.ALL) ? " (Recommended)" : "");
                }

                @Override
                public SigHash fromString(String string) {
                    return null;
                }
            });
            sigHash.valueProperty().addListener((observable, oldValue, newValue) -> {
                for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
                    psbtInput.setSigHash(newValue == SigHash.DEFAULT && !psbtInput.isTaproot() ? SigHash.ALL : newValue);
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
                walletInputTransactions = wallet.getWalletTransactions();
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
            Map<Script, WalletNode> changeOutputScripts = wallet.getWalletOutputScripts(wallet.getChangeKeyPurpose());
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
                        if(changeMap.containsKey(changeNode)) {
                            payments.add(new Payment(txOutput.getScript().getToAddress(), headersForm.getName(), txOutput.getValue(), false, Payment.Type.DEFAULT));
                        } else {
                            changeMap.put(changeNode, txOutput.getValue());
                        }
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
                    BlockTransactionHashIndex receivedTxo = getBlockTransactionOutput(txOutput);
                    payments.add(new Payment(txOutput.getScript().getToAddresses()[0], receivedTxo != null ? receivedTxo.getLabel() : null, txOutput.getValue(), false));
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

    private BlockTransactionHashIndex getBlockTransactionOutput(TransactionOutput txOutput) {
        for(Wallet openWallet : AppServices.get().getOpenWallets().keySet()) {
            Optional<BlockTransactionHashIndex> output = openWallet.getWalletTxos().keySet().stream().filter(ref -> ref.getHash().equals(txOutput.getHash()) && ref.getIndex() == txOutput.getIndex()).findFirst();
            if(output.isPresent()) {
                return output.get();
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

        if(blockTransaction.getHeight() > 0) {
            blockHeightField.setVisible(true);
            blockHeight.setText(Integer.toString(blockTransaction.getHeight()));
            blockHeight.setContextMenu(new BlockHeightContextMenu(blockTransaction));
        } else {
            blockHeightField.setVisible(false);
        }

        if(blockTransaction.getDate() != null) {
            blockTimestampField.setVisible(true);
            SimpleDateFormat dateFormat = new SimpleDateFormat(BLOCK_TIMESTAMP_DATE_FORMAT);
            blockTimestamp.setText(dateFormat.format(blockTransaction.getDate()));
            blockTimestamp.setContextMenu(new BlockHeightContextMenu(blockTransaction));
        } else {
            blockTimestampField.setVisible(false);
        }
    }

    private void initializeSignButton(Wallet signingWallet) {
        Optional<Keystore> softwareKeystore = signingWallet.getKeystores().stream().filter(keystore -> keystore.getSource().equals(KeystoreSource.SW_SEED)).findAny();
        Optional<Keystore> usbKeystore = signingWallet.getKeystores().stream().filter(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB) || keystore.getSource().equals(KeystoreSource.SW_WATCH)).findAny();
        Optional<Keystore> bip47Keystore = signingWallet.getKeystores().stream().filter(keystore -> keystore.getSource().equals(KeystoreSource.SW_PAYMENT_CODE)).findAny();
        Optional<Keystore> cardKeystore = signingWallet.getKeystores().stream().filter(keystore -> keystore.getWalletModel().isCard()).findAny();
        if(softwareKeystore.isEmpty() && usbKeystore.isEmpty() && bip47Keystore.isEmpty() && cardKeystore.isEmpty()) {
            signButton.setDisable(true);
        } else if(softwareKeystore.isEmpty() && bip47Keystore.isEmpty() && usbKeystore.isEmpty()) {
            Glyph tapGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WIFI);
            tapGlyph.setFontSize(20);
            signButton.setGraphic(tapGlyph);
        } else if(softwareKeystore.isEmpty() && bip47Keystore.isEmpty()) {
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
        public BlockHeightContextMenu(BlockTransaction blockTransaction) {
            if(blockTransaction.getHeight() > 0) {
                MenuItem copyBlockHeight = new MenuItem("Copy Block Height");
                copyBlockHeight.setOnAction(AE -> {
                    hide();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(Integer.toString(blockTransaction.getHeight()));
                    Clipboard.getSystemClipboard().setContent(content);
                });
                getItems().add(copyBlockHeight);
            }
            if(blockTransaction.getDate() != null) {
                MenuItem copyBlockTimestamp = new MenuItem("Copy Block Timestamp");
                copyBlockTimestamp.setOnAction(AE -> {
                    hide();
                    ClipboardContent content = new ClipboardContent();
                    SimpleDateFormat dateFormat = new SimpleDateFormat(BLOCK_TIMESTAMP_DATE_FORMAT);
                    content.putString(dateFormat.format(blockTransaction.getDate()));
                    Clipboard.getSystemClipboard().setContent(content);
                });
                getItems().add(copyBlockTimestamp);
            }
            if(blockTransaction.getBlockHash() != null && !blockTransaction.getBlockHash().equals(Sha256Hash.ZERO_HASH)) {
                MenuItem copyBlockHash = new MenuItem("Copy Block Hash");
                copyBlockHash.setOnAction(AE -> {
                    hide();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(blockTransaction.getBlockHash().toString());
                    Clipboard.getSystemClipboard().setContent(content);
                });
                getItems().add(copyBlockHash);
            }
        }
    }

    private void updateTxId() {
        id.setText(headersForm.getTransaction().calculateTxId(false).toString());
        if(!headersForm.isTransactionFinalized()) {
            addStyleClass(id, UNFINALIZED_TXID_CLASS);
            addStyleClass(size, UNFINALIZED_TXID_CLASS);
            addStyleClass(virtualSize, UNFINALIZED_TXID_CLASS);
            addStyleClass(feeRate, UNFINALIZED_TXID_CLASS);
            openBlockExplorer.setDisable(true);
        } else {
            id.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
            size.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
            virtualSize.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
            feeRate.getStyleClass().remove(UNFINALIZED_TXID_CLASS);
            openBlockExplorer.setDisable(Config.get().isBlockExplorerDisabled());
        }
    }

    private void addStyleClass(Node node, String styleClass) {
        if(!node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        }
    }

    public void copyId(ActionEvent event) {
        copyTxid.setSelected(false);
        ClipboardContent content = new ClipboardContent();
        content.putString(headersForm.getTransaction().calculateTxId(false).toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    public void openInBlockExplorer(ActionEvent event) {
        openBlockExplorer.setSelected(false);
        String txid = headersForm.getTransaction().calculateTxId(false).toString();
        AppServices.openBlockExplorer(txid);
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

        //TODO: Remove once Cobo Vault support has been removed
        boolean addLegacyEncodingOption = headersForm.getSigningWallet().getKeystores().stream().anyMatch(keystore -> keystore.getWalletModel().equals(WalletModel.COBO_VAULT));
        boolean addBbqrOption = headersForm.getSigningWallet().getKeystores().stream().anyMatch(keystore -> keystore.getWalletModel().equals(WalletModel.COLDCARD) || keystore.getSource().equals(KeystoreSource.SW_WATCH) || keystore.getSource().equals(KeystoreSource.SW_SEED));
        boolean selectBbqrOption = headersForm.getSigningWallet().getKeystores().stream().allMatch(keystore -> keystore.getWalletModel().equals(WalletModel.COLDCARD));

        //Don't include non witness utxo fields for segwit wallets when displaying the PSBT as a QR - it can add greatly to the time required for scanning
        boolean includeNonWitnessUtxos = !Arrays.asList(ScriptType.WITNESS_TYPES).contains(headersForm.getSigningWallet().getScriptType());
        byte[] psbtBytes = headersForm.getPsbt().serialize(true, includeNonWitnessUtxos);

        CryptoPSBT cryptoPSBT = new CryptoPSBT(psbtBytes);
        BBQR bbqr = addBbqrOption ? new BBQR(BBQRType.PSBT, psbtBytes) : null;
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(cryptoPSBT.toUR(), bbqr, addLegacyEncodingOption, true, selectBbqrOption);
        qrDisplayDialog.initOwner(toggleButton.getScene().getWindow());
        Optional<ButtonType> optButtonType = qrDisplayDialog.showAndWait();
        if(optButtonType.isPresent() && optButtonType.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            scanPSBT(event);
        }
    }

    public void scanPSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);

        QRScanDialog qrScanDialog = new QRScanDialog();
        qrScanDialog.initOwner(toggleButton.getScene().getWindow());
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.transaction != null) {
                EventManager.get().post(new ViewTransactionEvent(toggleButton.getScene().getWindow(), result.transaction));
            } else if(result.psbt != null) {
                EventManager.get().post(new ViewPSBTEvent(toggleButton.getScene().getWindow(), null, null, result.psbt));
            } else if(result.seed != null) {
                signFromSeed(result.seed);
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                showErrorDialog("Error scanning QR", result.exception.getMessage());
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "Cannot parse QR code into a transaction or seed.");
            }
        }
    }

    private void signFromSeed(DeterministicSeed seed) {
        try {
            String masterFingerprint = Keystore.fromSeed(seed, ScriptType.P2PKH.getDefaultDerivation()).getKeyDerivation().getMasterFingerprint();
            Wallet walletCopy = headersForm.getSigningWallet().copy();
            OptionalInt optIndex = IntStream.range(0, walletCopy.getKeystores().size())
                    .filter(i -> walletCopy.getKeystores().get(i).getKeyDerivation().getMasterFingerprint().equals(masterFingerprint)).findFirst();
            if(optIndex.isPresent()) {
                walletCopy.getKeystores().forEach(keystore -> {
                    keystore.setSeed(null);
                    keystore.setMasterPrivateExtendedKey(null);
                });
                Keystore original = walletCopy.getKeystores().get(optIndex.getAsInt());
                Keystore replacement = Keystore.fromSeed(seed, original.getKeyDerivation().getDerivation());
                walletCopy.getKeystores().set(optIndex.getAsInt(), replacement);
                signUnencryptedKeystores(walletCopy);
            } else {
                AppServices.showErrorDialog("Invalid seed", "The QR code contains a seed that does not match any of the keystores in the signing wallet.");
            }
        } catch(MnemonicException e) {
            log.error("Invalid seed", e);
            AppServices.showErrorDialog("Invalid seed", e.getMessage());
        } finally {
            seed.clear();
        }
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
            if(!file.getName().toLowerCase(Locale.ROOT).endsWith(".psbt")) {
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
        signDeviceKeystores();
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
            dlg.initOwner(signButton.getScene().getWindow());
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

    private void signDeviceKeystores() {
        if(headersForm.getPsbt().isSigned()) {
            return;
        }

        List<String> fingerprints = headersForm.getSigningWallet().getKeystores().stream().map(keystore -> keystore.getKeyDerivation().getMasterFingerprint()).collect(Collectors.toList());
        List<Device> signingDevices = AppServices.getDevices().stream().filter(device -> fingerprints.contains(device.getFingerprint())).collect(Collectors.toList());
        if(signingDevices.isEmpty() &&
                (headersForm.getSigningWallet().getKeystores().stream().noneMatch(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB) || keystore.getSource().equals(KeystoreSource.SW_WATCH) || keystore.getWalletModel().isCard()) ||
                        (headersForm.getSigningWallet().getKeystores().stream().anyMatch(keystore -> keystore.getSource().equals(KeystoreSource.SW_SEED)) && headersForm.getSigningWallet().getKeystores().stream().anyMatch(keystore -> keystore.getSource().equals(KeystoreSource.SW_WATCH))))) {
            return;
        }

        DeviceSignDialog dlg = new DeviceSignDialog(headersForm.getSigningWallet(), fingerprints, headersForm.getPsbt());
        dlg.initOwner(signButton.getScene().getWindow());
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
            try {
                headersForm.getSigningWallet().finalise(headersForm.getPsbt());
                EventManager.get().post(new PSBTFinalizedEvent(headersForm.getPsbt()));
            } catch(IllegalArgumentException e) {
                AppServices.showErrorDialog("Cannot finalize PSBT", e.getMessage());
                throw e;
            }
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

            String failMessage = "";
            if(workerStateEvent.getSource().getException() != null && workerStateEvent.getSource().getException().getMessage() != null) {
                failMessage = workerStateEvent.getSource().getException().getMessage();
            }

            UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
            if(failMessage.startsWith("min relay fee not met")) {
                AppServices.showErrorDialog("Error broadcasting transaction", "The fee rate for the signed transaction is below the minimum " + format.getCurrencyFormat().format(AppServices.getMinimumRelayFeeRate()) + " sats/vB. " +
                        "This usually happens because a keystore has created a signature that is larger than necessary.\n\n" +
                        "You can solve this by recreating the transaction with a slightly increased fee rate.");
            } else if(failMessage.startsWith("bad-txns-inputs-missingorspent")) {
                AppServices.showErrorDialog("Error broadcasting transaction", "The server returned an error indicating some or all of the UTXOs this transaction is spending are missing or have already been spent.");
            } else if(failMessage.contains("mempool min fee not met")) {
                Matcher minMempoolMatcher = MIN_MEMPOOL_FEE.matcher(failMessage);
                if(minMempoolMatcher.matches()) {
                    long requiredFee = Long.parseLong(minMempoolMatcher.group(2));
                    AppServices.showErrorDialog("Error broadcasting transaction", "The fee for the transaction was insufficient for relay by your connected server. Increase the fee to at least " + requiredFee + " sats to try again.");
                } else {
                    AppServices.showErrorDialog("Error broadcasting transaction", "The fee for the transaction was insufficient for relay by your connected server. Increase the fee to try again.");
                }
            } else if(failMessage.startsWith("insufficient fee, rejecting replacement")) {
                Matcher feeMatcher = RBF_INSUFFICIENT_FEE.matcher(failMessage);
                Matcher feeRateMatcher = RBF_INSUFFICIENT_FEE_RATE.matcher(failMessage);
                if(feeMatcher.matches() && fee.getValue() > 0) {
                    long currentAdditionalFee = (long)(Double.parseDouble(feeMatcher.group(1)) * Transaction.SATOSHIS_PER_BITCOIN);
                    long requiredAdditionalFee = (long)(Double.parseDouble(feeMatcher.group(2)) * Transaction.SATOSHIS_PER_BITCOIN);
                    long requiredFee = fee.getValue() - currentAdditionalFee + requiredAdditionalFee;
                    AppServices.showErrorDialog("Error broadcasting transaction", "The fee for the replacement transaction was insufficient. Increase the fee to at least " + requiredFee + " sats to try again.");
                } else if(feeRateMatcher.matches()) {
                    double requiredFeeRate = Double.parseDouble(feeRateMatcher.group(2)) * Transaction.SATOSHIS_PER_BITCOIN / 1000;
                    AppServices.showErrorDialog("Error broadcasting transaction", "The fee rate for the replacement transaction was insufficient. Increase the fee rate to at least " + format.getCurrencyFormat().format(requiredFeeRate) + " sats/vB to try again.");
                } else {
                    AppServices.showErrorDialog("Error broadcasting transaction", "The fee for the replacement transaction was insufficient. Increase the fee to try again.");
                }
            } else {
                AppServices.showErrorDialog("Error broadcasting transaction", "The server returned an error when broadcasting the transaction. The server response is contained in the log (See Help > Show Log File).");
            }
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
        if(headersForm.getSigningWallet() != null && headersForm.getSigningWallet().getWalletTransaction(txId) != null) {
            blockTransaction = headersForm.getSigningWallet().getWalletTransaction(txId);
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

            if(!event.getInputTransactions().isEmpty()) {
                Long feeAmt = calculateFee(event.getInputTransactions());
                if(feeAmt != null) {
                    updateFee(feeAmt);
                }

                Map<Sha256Hash, BlockTransaction> allFetchedInputTransactions = new HashMap<>(event.getInputTransactions());
                if(headersForm.getInputTransactions() != null) {
                    allFetchedInputTransactions.putAll(headersForm.getInputTransactions());
                }
                headersForm.setWalletTransaction(getWalletTransaction(allFetchedInputTransactions));
            }
        }
    }

    @Subscribe
    public void transactionFetchFailed(TransactionFetchFailedEvent event) {
        if(event.getTransaction().getTxId().equals(headersForm.getTransaction().getTxId())
                && !blockchainForm.isVisible() && !signingWalletForm.isVisible() && !signaturesForm.isVisible()) {
            blockchainForm.setVisible(true);
            blockStatus.setText("Unknown transaction status, server failed to respond");
            Glyph errorGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
            errorGlyph.setFontSize(12);
            blockStatus.setGraphic(errorGlyph);
            blockStatus.setContentDisplay(ContentDisplay.LEFT);
            errorGlyph.getStyleClass().add("failure");
            blockHeightField.setVisible(false);
            blockTimestampField.setVisible(false);
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        fee.refresh(event.getUnitFormat(), event.getBitcoinUnit());
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        if(id.getScene().getWindow().equals(event.getWindow()) && headersForm.getPsbt() != null && headersForm.getBlockTransaction() == null) {
            List<Wallet> availableWallets = event.getWallets().stream().filter(wallet -> wallet.canSign(headersForm.getPsbt())).sorted(new WalletSignComparator()).collect(Collectors.toList());
            if(availableWallets.isEmpty()) {
                for(Wallet wallet : event.getWalletsMap().keySet()) {
                    if(wallet.isValid() && !wallet.getSigningKeystores(headersForm.getPsbt()).isEmpty()) {
                        int currentGapLimit = wallet.getGapLimit();
                        Integer requiredGapLimit = wallet.getRequiredGapLimit(headersForm.getPsbt());
                        if(requiredGapLimit != null && requiredGapLimit > currentGapLimit) {
                            wallet.setGapLimit(requiredGapLimit);
                            EventManager.get().post(new WalletGapLimitChangedEvent(event.getStorage(wallet).getWalletId(wallet), wallet, currentGapLimit));
                            Platform.runLater(() -> EventManager.get().post(new RequestOpenWalletsEvent()));
                        }
                    }
                }
            }

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
            headersForm.setWalletTransaction(getWalletTransaction(headersForm.getInputTransactions()));
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
            BlockTransaction blockTransaction = event.getWallet().getWalletTransaction(txid);
            if(blockTransaction != null && blockTransaction.getLabel() == null) {
                String name = headersForm.getName();
                if(outputIndexLabels.size() > 1) {
                    StringJoiner joiner = new StringJoiner(", ");
                    outputIndexLabels.values().forEach(joiner::add);
                    name = joiner.toString();

                    Matcher matcher = EntryCell.REPLACED_BY_FEE_SUFFIX.matcher(name);
                    name = matcher.replaceAll("$1");
                    matcher.reset();
                    if(matcher.find()) {
                        name += matcher.group(2);
                    }
                }
                blockTransaction.setLabel(name != null && name.length() > 255 ? name.substring(0, 255) : name);
                changedLabelEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
            }

            for(WalletNode walletNode : event.getHistoryChangedNodes()) {
                for(BlockTransactionHashIndex output : walletNode.getTransactionOutputs()) {
                    if(output.getHash().equals(txid) && output.getLabel() == null) { //If we send to ourselves, usually change
                        String label = outputIndexLabels.containsKey((int)output.getIndex()) ? outputIndexLabels.get((int)output.getIndex()) : headersForm.getName();
                        output.setLabel(label + (walletNode.getKeyPurpose() == KeyPurpose.CHANGE ? (walletNode.getWallet().isBip47() ? " (sent)" : " (change)") : " (received)"));
                        changedLabelEntries.add(new HashIndexEntry(event.getWallet(), output, HashIndexEntry.Type.OUTPUT, walletNode.getKeyPurpose()));
                    }
                    if(output.getSpentBy() != null && output.getSpentBy().getHash().equals(txid) && output.getSpentBy().getLabel() == null && headersForm.getName() != null) { //The norm - sending out
                        output.getSpentBy().setLabel(headersForm.getName() + " (input)");
                        changedLabelEntries.add(new HashIndexEntry(event.getWallet(), output.getSpentBy(), HashIndexEntry.Type.INPUT, walletNode.getKeyPurpose()));
                    }
                }
            }

            if(!changedLabelEntries.isEmpty()) {
                Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(event.getWallet(), changedLabelEntries)));
            }
        } else if(headersForm.getBlockTransaction() != null && headersForm.getBlockTransaction().getHeight() <= 0) {
            BlockTransaction walletTransaction = event.getWallet().getWalletTransaction(headersForm.getBlockTransaction().getHash());
            if(walletTransaction != null && walletTransaction.getHeight() > 0) {
                headersForm.setBlockTransaction(walletTransaction);
                updateBlockchainForm(headersForm.getBlockTransaction(), AppServices.getCurrentBlockHeight());
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

    @Subscribe
    public void psbtReordered(PSBTReorderedEvent event) {
        if(event.getPsbt().equals(headersForm.getPsbt())) {
            updateTxId();
            headersForm.setWalletTransaction(getWalletTransaction(headersForm.getInputTransactions()));
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