package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.controlsfx.control.ToggleSwitch;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

public class InputController extends TransactionFormController implements Initializable {
    private InputForm inputForm;

    @FXML
    private Fieldset inputFieldset;

    @FXML
    private IdLabel outpoint;

    @FXML
    private Hyperlink linkedOutpoint;

    @FXML
    private CopyableCoinLabel spends;

    @FXML
    private CopyableLabel from;

    @FXML
    private AddressLabel address;

    @FXML
    private ScriptArea scriptSigArea;

    @FXML
    private VirtualizedScrollPane<CodeArea> redeemScriptScroll;

    @FXML
    private ScriptArea redeemScriptArea;

    @FXML
    private VirtualizedScrollPane<CodeArea> witnessScriptScroll;

    @FXML
    private ScriptArea witnessScriptArea;

    @FXML
    private VirtualizedScrollPane<CodeArea> witnessesScroll;

    @FXML
    private ScriptArea witnessesArea;

    @FXML
    private CopyableLabel signatures;

    @FXML
    private ToggleSwitch rbf;

    @FXML
    private ToggleGroup locktimeToggleGroup;

    @FXML
    private ToggleButton locktimeNoneType;

    @FXML
    private ToggleButton locktimeAbsoluteType;

    @FXML
    private ToggleButton locktimeRelativeType;

    @FXML
    private Fieldset locktimeFieldset;

    @FXML
    private Field locktimeAbsoluteField;

    @FXML
    private Field locktimeRelativeField;

    @FXML
    private CopyableLabel locktimeAbsolute;

    @FXML
    private IntegerSpinner locktimeRelativeBlocks;

    @FXML
    private RelativeTimelockSpinner locktimeRelativeSeconds;

    @FXML
    private ComboBox<String> locktimeRelativeCombo;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void initializeView() {
        TransactionInput txInput = inputForm.getTransactionInput();
        PSBTInput psbtInput = inputForm.getPsbtInput();

        inputForm.signingWalletProperty().addListener((observable, oldValue, signingWallet) -> {
            updateInputLegendFromWallet(txInput, signingWallet);
        });
        updateInputLegendFromWallet(txInput, inputForm.getWallet());

        initializeInputFields(txInput, psbtInput);
        initializeScriptFields(txInput, psbtInput);
        initializeStatusFields(txInput, psbtInput);
        initializeLocktimeFields(txInput);

        if(psbtInput != null) {
            inputForm.getSignatureKeystoreMap().addListener((MapChangeListener<TransactionSignature, Keystore>) c -> {
                updateSignatures(inputForm.getPsbtInput());
            });
        }
    }

    private String getLegendText(TransactionInput txInput) {
        return "Input #" + txInput.getIndex();
    }

    private void updateInputLegendFromWallet(TransactionInput txInput, Wallet wallet) {
        String baseText = getLegendText(txInput);
        if(wallet != null) {
            if(inputForm.isWalletTxo()) {
                inputFieldset.setText(baseText + " from " + wallet.getFullDisplayName());
                inputFieldset.setIcon(GlyphUtils.getTxoGlyph());
            } else {
                inputFieldset.setText(baseText + (txInput.isCoinBase() ? " - Coinbase" : " - External"));
                inputFieldset.setIcon(GlyphUtils.getMixGlyph());
            }
        } else {
            inputFieldset.setText(baseText + (txInput.isCoinBase() ? " - Coinbase" : " - External"));
            inputFieldset.setIcon(GlyphUtils.getExternalInputGlyph());
        }
    }

    private void initializeInputFields(TransactionInput txInput, PSBTInput psbtInput) {
        outpoint.managedProperty().bind(outpoint.visibleProperty());
        linkedOutpoint.managedProperty().bind(linkedOutpoint.visibleProperty());

        if(txInput.isCoinBase()) {
            outpoint.setText("Coinbase");
            long totalAmt = 0;
            for(TransactionOutput output : inputForm.getTransaction().getOutputs()) {
                totalAmt += output.getValue();
            }
            spends.setValue(totalAmt);
        } else if(inputForm.getInputTransactions() != null) {
            updateOutpoint(inputForm.getInputTransactions());
        } else {
            outpoint.setVisible(true);
            linkedOutpoint.setVisible(false);
            outpoint.setText(txInput.getOutpoint().getHash().toString() + ":" + txInput.getOutpoint().getIndex());
        }

        from.setVisible(false);
        if(psbtInput != null) {
            TransactionOutput output = psbtInput.getUtxo();
            updateSpends(output);
        } else if(inputForm.getInputTransactions() != null) {
            updateSpends(inputForm.getInputTransactions());
        }
    }

    private void updateOutpoint(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        outpoint.setVisible(false);
        linkedOutpoint.setVisible(true);

        TransactionInput txInput = inputForm.getTransactionInput();
        linkedOutpoint.setText(txInput.getOutpoint().getHash().toString() + ":" + txInput.getOutpoint().getIndex());
        linkedOutpoint.setOnAction(event -> {
            BlockTransaction linkedTransaction = inputTransactions.get(txInput.getOutpoint().getHash());
            EventManager.get().post(new ViewTransactionEvent(linkedOutpoint.getScene().getWindow(), linkedTransaction, TransactionView.OUTPUT, (int)txInput.getOutpoint().getIndex()));
        });
        linkedOutpoint.setContextMenu(new TransactionReferenceContextMenu(linkedOutpoint.getText()));
    }

    private void updateSpends(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        TransactionInput txInput = inputForm.getTransactionInput();
        if(!txInput.isCoinBase()) {
            BlockTransaction blockTransaction = inputTransactions.get(txInput.getOutpoint().getHash());
            if(blockTransaction == null) {
                if(inputForm.getIndex() < inputForm.getMaxInputFetched()) {
                    throw new IllegalStateException("Could not retrieve block transaction for input #" + inputForm.getIndex());
                } else {
                    //Still paging
                    return;
                }
            }

            TransactionOutput output = blockTransaction.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
            updateSpends(output);
        }
    }

    private void updateSpends(TransactionOutput output) {
        if (output != null) {
            spends.setValue(output.getValue());
            try {
                Address[] addresses = output.getScript().getToAddresses();
                from.setVisible(true);
                if (addresses.length == 1) {
                    address.setAddress(addresses[0]);
                } else {
                    address.setText("multiple addresses");
                }
            } catch (NonStandardScriptException e) {
                //ignore
            }
        }
    }

    private void initializeScriptFields(TransactionInput txInput, PSBTInput psbtInput) {
        initializeScriptField(scriptSigArea);
        initializeScriptField(redeemScriptArea);
        initializeScriptField(witnessesArea);
        initializeScriptField(witnessScriptArea);

        updateScriptFields(txInput, psbtInput);
    }

    private void updateScriptFields(TransactionInput txInput, PSBTInput psbtInput) {
        //Don't use PSBT data if txInput has scriptSig or witness data. This happens when a tx has been extracted from a PSBT
        if(txInput.getScriptBytes().length > 0 || txInput.hasWitness()) {
            psbtInput = null;
        }

        scriptSigArea.clear();
        redeemScriptArea.clear();
        witnessesArea.clear();
        witnessScriptArea.clear();

        //TODO: While we immediately check if the referenced transaction output is P2SH, where this is not present getting the first nested script is not safe
        Script redeemScript = txInput.getScriptSig().getFirstNestedScript();
        if(redeemScript != null && inputForm.getReferencedTransactionOutput() != null) {
            Script lockingScript = inputForm.getReferencedTransactionOutput().getScript();
            if(!ScriptType.P2SH.isScriptType(lockingScript)) {
                redeemScript = null;
            }
        }

        if(redeemScript == null && psbtInput != null && psbtInput.getRedeemScript() != null) {
            redeemScriptArea.addPSBTDecoration("PSBT Redeem Script", "non-final");
            redeemScript = psbtInput.getRedeemScript();
        }
        if(redeemScript == null && psbtInput != null && psbtInput.getFinalScriptSig() != null) {
            redeemScriptArea.addPSBTDecoration("PSBT Final ScriptSig", "final");
            redeemScript = psbtInput.getFinalScriptSig().getFirstNestedScript();
        }

        if(txInput.getScriptSig().isEmpty() && psbtInput != null && psbtInput.getFinalScriptSig() != null) {
            scriptSigArea.appendScript(psbtInput.getFinalScriptSig(), redeemScript, null);
            scriptSigArea.addPSBTDecoration("PSBT Final ScriptSig", "final");
        } else {
            scriptSigArea.appendScript(txInput.getScriptSig(), redeemScript, null);
        }

        if(redeemScript != null) {
            redeemScriptArea.setDisable(false);
            redeemScriptArea.appendScript(redeemScript);
        } else {
            redeemScriptScroll.setDisable(true);
        }

        Script witnesses = null;
        Script witnessScript = null;

        if(txInput.hasWitness()) {
            witnesses = new Script(txInput.getWitness().asScriptChunks());
            witnessScript = txInput.getWitness().getWitnessScript();
        } else if(psbtInput != null) {
            if(psbtInput.getFinalScriptWitness() != null) {
                witnesses = new Script(psbtInput.getFinalScriptWitness().asScriptChunks());
                witnessScript = psbtInput.getFinalScriptWitness().getWitnessScript();
                witnessesArea.addPSBTDecoration("PSBT Final ScriptWitness", "final");
                witnessScriptArea.addPSBTDecoration("PSBT Final ScriptWitness", "final");
            } else if(psbtInput.getWitnessScript() != null) {
                witnessScript = psbtInput.getWitnessScript();
                witnessScriptArea.addPSBTDecoration("PSBT Witness Script", "non-final");
            }
        }

        if(witnesses != null) {
            witnessesScroll.setDisable(false);
            witnessesArea.appendScript(witnesses, null, witnessScript);
        } else {
            witnessesScroll.setDisable(true);
        }

        if(witnessScript != null) {
            witnessScriptScroll.setDisable(false);
            witnessScriptArea.appendScript(witnessScript);
        } else {
            witnessScriptScroll.setDisable(true);
        }
    }

    private void initializeStatusFields(TransactionInput txInput, PSBTInput psbtInput) {
        updateSignatures(psbtInput);

        Transaction transaction = inputForm.getTransaction();
        rbf.setSelected(txInput.isReplaceByFeeEnabled());
        rbf.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue) {
                if(txInput.isAbsoluteTimeLockDisabled()) {
                    locktimeToggleGroup.selectToggle(locktimeAbsoluteType);
                } else if(txInput.isAbsoluteTimeLocked()) {
                    txInput.setSequenceNumber(TransactionInput.SEQUENCE_RBF_ENABLED);
                    if(oldValue != null) {
                        EventManager.get().post(new TransactionChangedEvent(transaction));
                    }
                }
            } else {
                if(txInput.isAbsoluteTimeLocked()) {
                    txInput.setSequenceNumber(TransactionInput.SEQUENCE_LOCKTIME_DISABLED - 1);
                    if(oldValue != null) {
                        EventManager.get().post(new TransactionChangedEvent(transaction));
                    }
                } else if(txInput.isRelativeTimeLocked()) {
                    locktimeToggleGroup.selectToggle(locktimeAbsoluteType);
                }
            }
        });
        rbf.setDisable(!inputForm.isEditable());
    }

    private void updateSignatures(PSBTInput psbtInput) {
        signatures.setText("Unknown");
        if(inputForm.getPsbtInput() != null) {
            int reqSigs = -1;
            if(psbtInput.getUtxo() != null && psbtInput.getSigningScript() != null) {
                try {
                    reqSigs = psbtInput.getSigningScript().getNumRequiredSignatures();
                } catch (NonStandardScriptException e) {
                    //TODO: Handle unusual transaction sig
                }
            }

            int foundSigs = psbtInput.getSignatures().size();
            signatures.setText(foundSigs + "/" + (reqSigs < 0 ? "?" : reqSigs));
        }
    }

    private void initializeLocktimeFields(TransactionInput txInput) {
        Transaction transaction = inputForm.getTransaction();
        locktimeToggleGroup.selectedToggleProperty().addListener((ov, old_toggle, new_toggle) -> {
            if(locktimeToggleGroup.getSelectedToggle() != null) {
                String selection = locktimeToggleGroup.getSelectedToggle().getUserData().toString();
                if(selection.equals("none")) {
                    locktimeFieldset.getChildren().removeAll(locktimeRelativeField, locktimeAbsoluteField);
                    locktimeFieldset.getChildren().add(locktimeAbsoluteField);
                    updateAbsoluteLocktimeField(transaction);
                    locktimeAbsoluteField.setDisable(true);
                    txInput.setSequenceNumber(TransactionInput.SEQUENCE_LOCKTIME_DISABLED);
                    rbf.setSelected(false);
                    if(old_toggle != null) {
                        EventManager.get().post(new TransactionChangedEvent(transaction));
                    }
                } else if(selection.equals("absolute")) {
                    locktimeFieldset.getChildren().removeAll(locktimeRelativeField, locktimeAbsoluteField);
                    locktimeFieldset.getChildren().add(locktimeAbsoluteField);
                    updateAbsoluteLocktimeField(transaction);
                    locktimeAbsoluteField.setDisable(false);
                    if(rbf.selectedProperty().getValue()) {
                        txInput.setSequenceNumber(TransactionInput.SEQUENCE_RBF_ENABLED);
                    } else {
                        txInput.setSequenceNumber(TransactionInput.SEQUENCE_LOCKTIME_DISABLED - 1);
                    }
                    if(old_toggle != null) {
                        EventManager.get().post(new TransactionChangedEvent(transaction));
                    }
                } else {
                    locktimeFieldset.getChildren().removeAll(locktimeRelativeField, locktimeAbsoluteField);
                    locktimeFieldset.getChildren().add(locktimeRelativeField);
                    if(locktimeRelativeCombo.getValue() == null) {
                        locktimeRelativeCombo.getSelectionModel().select(0);
                    } else {
                        setRelativeLocktime(txInput, transaction, old_toggle != null);
                    }
                    rbf.setSelected(true);
                }
            }
        });

        locktimeRelativeBlocks.setValueFactory(new IntegerSpinner.ValueFactory(0, (int)TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK, 0));
        locktimeRelativeBlocks.managedProperty().bind(locktimeRelativeBlocks.visibleProperty());
        locktimeRelativeSeconds.managedProperty().bind(locktimeRelativeSeconds.visibleProperty());
        locktimeRelativeCombo.getSelectionModel().selectedItemProperty().addListener((ov, old_toggle, new_toggle) -> {
            boolean blocks = locktimeRelativeCombo.getValue().equals("blocks");
            locktimeRelativeSeconds.setVisible(!blocks);
            locktimeRelativeBlocks.setVisible(blocks);
            setRelativeLocktime(txInput, transaction, old_toggle != null);
        });

        locktimeRelativeType.setDisable(!transaction.isRelativeLocktimeAllowed());
        if(txInput.isAbsoluteTimeLockDisabled()) {
            locktimeToggleGroup.selectToggle(locktimeNoneType);
        } else if(txInput.isAbsoluteTimeLocked()) {
            locktimeToggleGroup.selectToggle(locktimeAbsoluteType);
        } else {
            if(txInput.isRelativeTimeLockedInBlocks()) {
                locktimeRelativeBlocks.valueFactoryProperty().get().setValue((int)txInput.getRelativeLocktime());
                locktimeRelativeCombo.getSelectionModel().select(0);
            } else {
                locktimeRelativeSeconds.valueFactoryProperty().get().setValue(Duration.ofSeconds(txInput.getRelativeLocktime() * TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT));
                locktimeRelativeCombo.getSelectionModel().select(1);
            }
            locktimeToggleGroup.selectToggle(locktimeRelativeType);
        }

        locktimeRelativeBlocks.valueProperty().addListener((obs, oldValue, newValue) -> {
            if(newValue == null || newValue < 0 || newValue > TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK) {
                return;
            }

            setRelativeLocktime(txInput, transaction, oldValue != null);
        });
        locktimeRelativeSeconds.valueProperty().addListener((obs, oldValue, newValue) -> {
            setRelativeLocktime(txInput, transaction, oldValue != null);
        });

        locktimeNoneType.setDisable(!inputForm.isEditable());
        locktimeAbsoluteType.setDisable(!inputForm.isEditable());
        locktimeRelativeType.setDisable(!inputForm.isEditable());
        locktimeRelativeBlocks.setDisable(!inputForm.isEditable());
        locktimeRelativeSeconds.setDisable(!inputForm.isEditable());
        locktimeRelativeCombo.setDisable(!inputForm.isEditable());
    }

    private void updateAbsoluteLocktimeField(Transaction transaction) {
        long locktime = transaction.getLocktime();
        if(locktime < Transaction.MAX_BLOCK_LOCKTIME) {
            locktimeAbsoluteField.setText("Block:");
            locktimeAbsolute.setText(Long.toString(locktime));
        } else {
            locktimeAbsoluteField.setText("Date:");
            LocalDateTime localDateTime = Instant.ofEpochSecond(locktime).atZone(ZoneId.systemDefault()).toLocalDateTime();
            locktimeAbsolute.setText(DateTimeFormatter.ofPattern(HeadersController.LOCKTIME_DATE_FORMAT).format(localDateTime));
        }
    }

    private void setRelativeLocktime(TransactionInput txInput, Transaction transaction, boolean changed) {
        String relativeSelection = locktimeRelativeCombo.getValue();
        if(relativeSelection.equals("blocks")) {
            Integer value = locktimeRelativeBlocks.getValue();
            txInput.setSequenceNumber(value & TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK);
        } else {
            long value = locktimeRelativeSeconds.getValue().toSeconds() / TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT;
            txInput.setSequenceNumber((value & TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK) | TransactionInput.RELATIVE_TIMELOCK_TYPE_FLAG);
        }
        if(changed) {
            EventManager.get().post(new TransactionChangedEvent(transaction));
        }
    }

    public void setModel(InputForm form) {
        this.inputForm = form;
        initializeView();
    }

    @Override
    protected TransactionForm getTransactionForm() {
        return inputForm;
    }

    @Override
    protected String describeScriptChunk(ScriptChunk chunk) {
        String chunkString = super.describeScriptChunk(chunk);

        ECKey pubKey = null;
        if(chunk.isSignature()) {
            if(inputForm.getPsbtInput() != null) {
                TransactionSignature signature = chunk.getSignature();
                pubKey = inputForm.getPsbtInput().getKeyForSignature(signature);
            }
        } else if(chunk.isPubKey()) {
            pubKey = chunk.getPubKey();
        }

        if(inputForm.getPsbtInput() != null) {
            KeyDerivation derivation = inputForm.getPsbtInput().getKeyDerivation(pubKey);
            if(derivation != null) {
                return "[" + derivation.toString() + "] " + chunkString;
            }
        }

        return chunkString;
    }

    @Subscribe
    public void blockTransactionFetched(BlockTransactionFetchedEvent event) {
        if(event.getTxId().equals(inputForm.getTransaction().getTxId()) && !event.getInputTransactions().isEmpty() && inputForm.getIndex() >= event.getPageStart() && inputForm.getIndex() < event.getPageEnd()) {
            updateOutpoint(event.getInputTransactions());
            if(inputForm.getPsbt() == null) {
                updateSpends(event.getInputTransactions());
            }
        }
    }

    @Subscribe
    public void transactionLocktimeChanged(TransactionLocktimeChangedEvent event) {
        if(event.getTransaction().equals(inputForm.getTransaction())) {
            locktimeAbsolute.setText(Long.toString(event.getTransaction().getLocktime()));
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        spends.refresh(event.getUnitFormat(), event.getBitcoinUnit());
    }

    @Subscribe
    public void finalizeTransaction(FinalizeTransactionEvent event) {
        if(inputForm.getPsbt() == event.getPsbt()) {
            rbf.setDisable(true);
            locktimeNoneType.setDisable(true);
            locktimeAbsoluteType.setDisable(true);
            locktimeRelativeType.setDisable(true);
            locktimeRelativeBlocks.setDisable(true);
            locktimeRelativeSeconds.setDisable(true);
            locktimeRelativeCombo.setDisable(true);
        }
    }

    @Subscribe
    public void psbtCombined(PSBTCombinedEvent event) {
        if(event.getPsbt().equals(inputForm.getPsbt())) {
            updateSpends(inputForm.getPsbtInput().getUtxo());
            updateScriptFields(inputForm.getTransactionInput(), inputForm.getPsbtInput());
            updateSignatures(inputForm.getPsbtInput());
        }
    }

    @Subscribe
    public void psbtFinalized(PSBTFinalizedEvent event) {
        if(event.getPsbt().equals(inputForm.getPsbt())) {
            updateSpends(inputForm.getPsbtInput().getUtxo());
            updateScriptFields(inputForm.getTransactionInput(), inputForm.getPsbtInput());
            updateSignatures(inputForm.getPsbtInput());
        }
    }

    @Subscribe
    public void transactionExtracted(TransactionExtractedEvent event) {
        if(event.getPsbt().equals(inputForm.getPsbt())) {
            updateScriptFields(event.getFinalTransaction().getInputs().get(inputForm.getIndex()), null);
        }
    }

    @Subscribe
    public void psbtReordered(PSBTReorderedEvent event) {
        if(event.getPsbt().equals(inputForm.getPsbt())) {
            updateInputLegendFromWallet(inputForm.getTransactionInput(), null);
        }
    }
}
