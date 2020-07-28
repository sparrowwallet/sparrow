package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.Glyph;
import tornadofx.control.DateTimePicker;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import com.google.common.eventbus.Subscribe;
import tornadofx.control.Form;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class HeadersController extends TransactionFormController implements Initializable {
    public static final String LOCKTIME_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String BLOCK_TIMESTAMP_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss ZZZ";

    private HeadersForm headersForm;

    @FXML
    private IdLabel id;

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
    private DateTimePicker locktimeDate;

    @FXML
    private CopyableLabel size;

    @FXML
    private CopyableLabel virtualSize;

    @FXML
    private CoinLabel fee;

    @FXML
    private CopyableLabel feeRate;

    @FXML
    private Form blockchainForm;

    @FXML
    private CopyableLabel blockStatus;

    @FXML
    private CopyableLabel blockHeight;

    @FXML
    private CopyableLabel blockTimestamp;

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
    private Button signButton;

    @FXML
    private Form broadcastForm;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    void setModel(HeadersForm form) {
        this.headersForm = form;
        initializeView();
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

        String type = "Legacy";
        if(tx.isSegwit()) {
            type = "Segwit";
            if(tx.getSegwitVersion() == 2) {
                type = "Taproot";
            }
        }
        segwit.setText(type);

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
                        tx.setLocktime(date.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
                        if(old_toggle != null) {
                            EventManager.get().post(new TransactionChangedEvent(tx));
                        }
                    }
                }
            }
        });

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
            if(oldValue != null) {
                EventManager.get().post(new TransactionChangedEvent(tx));
                EventManager.get().post(new TransactionLocktimeChangedEvent(tx));
            }
        });

        locktimeDate.setFormat(LOCKTIME_DATE_FORMAT);
        locktimeDate.dateTimeValueProperty().addListener((obs, oldValue, newValue) -> {
            tx.setLocktime(newValue.toEpochSecond(OffsetDateTime.now(ZoneId.systemDefault()).getOffset()));
            if(oldValue != null) {
                EventManager.get().post(new TransactionChangedEvent(tx));
            }
        });

        locktimeNoneType.setDisable(!headersForm.isEditable());
        locktimeBlockType.setDisable(!headersForm.isEditable());
        locktimeDateType.setDisable(!headersForm.isEditable());
        locktimeBlock.setDisable(!headersForm.isEditable());
        locktimeDate.setDisable(!headersForm.isEditable());

        size.setText(tx.getSize() + " B");
        virtualSize.setText(tx.getVirtualSize() + " vB");

        Long feeAmt = null;
        if(headersForm.getPsbt() != null) {
            feeAmt = headersForm.getPsbt().getFee();
        } else if(headersForm.getTransaction().getInputs().size() == 1 && headersForm.getTransaction().getInputs().get(0).isCoinBase()) {
            feeAmt = 0L;
        } else if(headersForm.getInputTransactions() != null) {
            feeAmt = calculateFee(headersForm.getInputTransactions());
        }

        if(feeAmt != null) {
            updateFee(feeAmt);
        }

        blockchainForm.managedProperty().bind(blockchainForm.visibleProperty());

        signingWalletForm.managedProperty().bind(signingWalletForm.visibleProperty());
        sigHashForm.managedProperty().bind(sigHashForm.visibleProperty());
        sigHashForm.visibleProperty().bind(signingWalletForm.visibleProperty());
        finalizeButtonBox.managedProperty().bind(finalizeButtonBox.visibleProperty());
        finalizeButtonBox.visibleProperty().bind(signingWalletForm.visibleProperty());

        signaturesForm.managedProperty().bind(signaturesForm.visibleProperty());
        broadcastForm.managedProperty().bind(broadcastForm.visibleProperty());

        blockchainForm.setVisible(false);
        signingWalletForm.setVisible(false);
        signaturesForm.setVisible(false);
        broadcastForm.setVisible(false);

        if(headersForm.getBlockTransaction() != null) {
            blockchainForm.setVisible(true);
            updateBlockchainForm(headersForm.getBlockTransaction());
        } else if(headersForm.getPsbt() != null) {
            PSBT psbt = headersForm.getPsbt();

            if(headersForm.isEditable()) {
                signingWalletForm.setVisible(true);
            } else if(headersForm.getPsbt().isSigned()) {
                broadcastForm.setVisible(true);
            } else {
                signaturesForm.setVisible(true);
            }

            EventManager.get().post(new RequestOpenWalletsEvent());

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
            sigHash.setValue(psbtSigHash);
            sigHash.valueProperty().addListener((observable, oldValue, newValue) -> {
                for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
                    psbtInput.setSigHash(newValue);
                }
            });

            headersForm.signingWalletProperty().addListener((observable, oldValue, signingWallet) -> {
                initializeSignButton(signingWallet);
                updateSignedKeystores(signingWallet);

                int threshold = signingWallet.getDefaultPolicy().getNumSignaturesRequired();
                signaturesProgressBar.initialize(headersForm.getSignedKeystores(), threshold);
            });
        }
    }

    private Long calculateFee(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        long feeAmt = 0L;
        for(TransactionInput input : headersForm.getTransaction().getInputs()) {
            if(input.isCoinBase()) {
                return 0L;
            }

            BlockTransaction inputTx = inputTransactions.get(input.getOutpoint().getHash());
            if(inputTx == null) {
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
        feeRate.setText(String.format("%.2f", feeRateAmt) + " sats/vByte");
    }

    private void updateBlockchainForm(BlockTransaction blockTransaction) {
        blockchainForm.setVisible(true);

        Integer currentHeight = AppController.getCurrentBlockHeight();
        if(currentHeight == null) {
            blockStatus.setText("Unknown");
        } else {
            int confirmations = currentHeight - blockTransaction.getHeight() + 1;
            blockStatus.setText(confirmations + " Confirmations");
        }

        blockHeight.setText(Integer.toString(blockTransaction.getHeight()));

        SimpleDateFormat dateFormat = new SimpleDateFormat(BLOCK_TIMESTAMP_DATE_FORMAT);
        blockTimestamp.setText(dateFormat.format(blockTransaction.getDate()));

        blockHash.managedProperty().bind(blockHash.visibleProperty());
        if(blockTransaction.getBlockHash() != null) {
            blockHash.setVisible(true);
            blockHash.setText(blockTransaction.getBlockHash().toString());
            blockHash.setContextMenu(new BlockHeightContextMenu(blockTransaction.getBlockHash()));
        } else {
            blockHash.setVisible(false);
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
        if(headersForm.getPsbt() != null && headersForm.isEditable()) {
            id.getStyleClass().add("unfinalized-psbt");
        }
    }

    public void copyId(ActionEvent event) {
        ClipboardContent content = new ClipboardContent();
        content.putString(headersForm.getTransaction().getTxId().toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    public void openWallet(ActionEvent event) {
        EventManager.get().post(new RequestWalletOpenEvent());
    }

    public void finalizeTransaction(ActionEvent event) {
        EventManager.get().post(new FinalizeTransactionEvent(headersForm.getPsbt(), signingWallet.getValue()));
    }

    public void showPSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);

        headersForm.getSignedKeystores().add(headersForm.getSigningWallet().getKeystores().get(0));
    }

    public void scanPSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);
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

        File file = fileChooser.showSaveDialog(window);
        if(file != null) {
            try {
                try(PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    writer.print(headersForm.getPsbt().toBase64String());
                }
            } catch(IOException e) {
                AppController.showErrorDialog("Error saving PSBT", "Cannot write to " + file.getAbsolutePath());
            }
        }
    }

    public void loadPSBT(ActionEvent event) {
        ToggleButton toggleButton = (ToggleButton)event.getSource();
        toggleButton.setSelected(false);

        EventManager.get().post(new RequestTransactionOpenEvent());
    }

    public void signPSBT(ActionEvent event) {
        signSoftwareKeystores();
        signUsbKeystores();
    }

    private void signSoftwareKeystores() {
        if(headersForm.getSigningWallet().getKeystores().stream().noneMatch(Keystore::hasSeed)) {
            return;
        }

        Wallet copy = headersForm.getSigningWallet().copy();
        File file = headersForm.getAvailableWallets().get(headersForm.getSigningWallet()).getWalletFile();

        if(copy.isEncrypted()) {
            WalletPasswordDialog dlg = new WalletPasswordDialog(WalletPasswordDialog.PasswordRequirement.LOAD);
            Optional<SecureString> password = dlg.showAndWait();
            if(password.isPresent()) {
                Storage.DecryptWalletService decryptWalletService = new Storage.DecryptWalletService(copy, password.get());
                decryptWalletService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(file, TimedEvent.Action.END, "Done"));
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    signUnencryptedKeystores(decryptedWallet);
                });
                decryptWalletService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(file, TimedEvent.Action.END, "Failed"));
                    AppController.showErrorDialog("Incorrect Password", decryptWalletService.getException().getMessage());
                });
                EventManager.get().post(new StorageEvent(file, TimedEvent.Action.START, "Decrypting wallet..."));
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
            AppController.showErrorDialog("Failed to Sign", e.getMessage());
        }
    }

    private void signUsbKeystores() {
        if(headersForm.getSigningWallet().getKeystores().stream().noneMatch(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB))) {
            return;
        }

        DeviceSignDialog dlg = new DeviceSignDialog(headersForm.getPsbt());
        Optional<PSBT> optionalSignedPsbt = dlg.showAndWait();
        if(optionalSignedPsbt.isPresent()) {
            PSBT signedPsbt = optionalSignedPsbt.get();
            headersForm.getPsbt().combine(signedPsbt);
            EventManager.get().post(new PSBTCombinedEvent(headersForm.getPsbt()));
        }
    }

    private void updateSignedKeystores(Wallet signingWallet) {
        Map<PSBTInput, List<Keystore>> signedKeystoresMap = signingWallet.getSignedKeystores(headersForm.getPsbt());
        Optional<List<Keystore>> optSignedKeystores = signedKeystoresMap.values().stream().filter(list -> !list.isEmpty()).min(Comparator.comparingInt(List::size));
        optSignedKeystores.ifPresent(signedKeystores -> {
            List<Keystore> newSignedKeystores = new ArrayList<>(signedKeystores);
            newSignedKeystores.removeAll(headersForm.getSignedKeystores());
            headersForm.getSignedKeystores().addAll(newSignedKeystores);
        });
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
        }
    }

    @Subscribe
    public void blockTransactionFetched(BlockTransactionFetchedEvent event) {
        if(event.getTxId().equals(headersForm.getTransaction().getTxId())) {
            if(event.getBlockTransaction() != null) {
                updateBlockchainForm(event.getBlockTransaction());
            }

            Long feeAmt = calculateFee(event.getInputTransactions());
            if(feeAmt != null) {
                updateFee(feeAmt);
            }
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        fee.refresh(event.getBitcoinUnit());
    }

    @Subscribe
    public void openWallets(OpenWalletsEvent event) {
        if(headersForm.getPsbt() != null && headersForm.isEditable()) {
            List<Wallet> availableWallets = event.getWallets().stream().filter(wallet -> wallet.canSign(headersForm.getPsbt())).collect(Collectors.toList());
            Map<Wallet, Storage> availableWalletsMap = new LinkedHashMap<>(event.getWalletsMap());
            availableWalletsMap.keySet().retainAll(availableWallets);
            headersForm.getAvailableWallets().keySet().retainAll(availableWallets);
            headersForm.getAvailableWallets().putAll(availableWalletsMap);

            signingWallet.setItems(FXCollections.observableList(availableWallets));
            if(!availableWallets.isEmpty()) {
                if(availableWallets.contains(headersForm.getSigningWallet())) {
                    signingWallet.setValue(headersForm.getSigningWallet());
                } else {
                    signingWallet.setValue(availableWallets.get(0));
                }
                noWalletsWarning.setVisible(false);
                signingWallet.setVisible(true);
                finalizeTransaction.setDisable(false);
            } else {
                noWalletsWarning.setVisible(true);
                signingWallet.setVisible(false);
                finalizeTransaction.setDisable(true);
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
            id.getStyleClass().remove("unfinalized-psbt");

            headersForm.setSigningWallet(event.getSigningWallet());

            signingWalletForm.setVisible(false);
            signaturesForm.setVisible(true);
        }
    }

    @Subscribe
    public void psbtCombined(PSBTCombinedEvent event) {
        if(event.getPsbt().equals(headersForm.getPsbt()) && headersForm.getSigningWallet() != null) {
            updateSignedKeystores(headersForm.getSigningWallet());
        }
    }

    @Subscribe
    public void keystoreSigned(KeystoreSignedEvent event) {
        if(headersForm.getSignedKeystores().contains(event.getKeystore()) && headersForm.getPsbt() != null) {
            if(headersForm.getPsbt().isSigned()) {
                headersForm.getSigningWallet().finalise(headersForm.getPsbt());
                EventManager.get().post(new PSBTFinalizedEvent(headersForm.getPsbt()));
            }
        }
    }
}