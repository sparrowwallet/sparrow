package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.RelativeTimelockSpinner;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.controlsfx.control.ToggleSwitch;
import org.fxmisc.richtext.CodeArea;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class InputController extends TransactionFormController implements Initializable {
    private InputForm inputForm;

    @FXML
    private Fieldset inputFieldset;

    @FXML
    private TextField outpoint;

    @FXML
    private Button outpointSelect;

    @FXML
    private TextField spends;

    @FXML
    private Label from;

    @FXML
    private TextField address;

    @FXML
    private CodeArea scriptSigArea;

    @FXML
    private VirtualizedScrollPane<CodeArea> redeemScriptScroll;

    @FXML
    private CodeArea redeemScriptArea;

    @FXML
    private VirtualizedScrollPane<CodeArea> witnessScriptScroll;

    @FXML
    private CodeArea witnessScriptArea;

    @FXML
    private VirtualizedScrollPane<CodeArea> witnessesScroll;

    @FXML
    private CodeArea witnessesArea;

    @FXML
    private TextField signatures;

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
    private TextField locktimeAbsolute;

    @FXML
    private Spinner<Integer> locktimeRelativeBlocks;

    @FXML
    private RelativeTimelockSpinner locktimeRelativeSeconds;

    @FXML
    private ComboBox<String> locktimeRelativeCombo;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void initializeView() {
        TransactionInput txInput = inputForm.getTransactionInput();
        PSBTInput psbtInput = inputForm.getPsbtInput();

        initializeInputFields(txInput, psbtInput);
        initializeScriptFields(txInput, psbtInput);
        initializeStatusFields(txInput);
        initializeLocktimeFields(txInput);
    }

    private void initializeInputFields(TransactionInput txInput, PSBTInput psbtInput) {
        inputFieldset.setText("Input #" + txInput.getIndex());
        outpoint.setText(txInput.getOutpoint().getHash().toString() + ":" + txInput.getOutpoint().getIndex());

        spends.setText("Unknown");
        from.setVisible(false);
        if(psbtInput != null) {
            TransactionOutput output = null;
            if(psbtInput.getNonWitnessUtxo() != null) {
                output = psbtInput.getNonWitnessUtxo().getOutputs().get(txInput.getIndex());
            } else if(psbtInput.getWitnessUtxo() != null) {
                output = psbtInput.getWitnessUtxo();
            }

            if(output != null) {
                spends.setText(output.getValue() + " sats");
                try {
                    Address[] addresses = output.getScript().getToAddresses();
                    from.setVisible(true);
                    if(addresses.length == 1) {
                        address.setText(addresses[0].getAddress());
                    } else {
                        address.setText("multiple addresses");
                    }
                } catch(NonStandardScriptException e) {
                    //ignore
                }
            }
        }

        //TODO: Enable select outpoint when wallet present
        outpointSelect.setDisable(true);
    }

    private void initializeScriptFields(TransactionInput txInput, PSBTInput psbtInput) {
        //TODO: Is this safe?
        Script redeemScript = txInput.getScriptSig().getFirstNestedScript();
        if(redeemScript == null && psbtInput != null && psbtInput.getRedeemScript() != null) {
            redeemScript = psbtInput.getRedeemScript();
        }
        if(redeemScript == null && psbtInput != null && psbtInput.getFinalScriptSig() != null) {
            redeemScript = psbtInput.getFinalScriptSig().getFirstNestedScript();
        }

        scriptSigArea.clear();
        if(txInput.getScriptSig().isEmpty() && psbtInput != null && psbtInput.getFinalScriptSig() != null) {
            appendScript(scriptSigArea, psbtInput.getFinalScriptSig(), redeemScript, null);
        } else {
            appendScript(scriptSigArea, txInput.getScriptSig(), redeemScript, null);
        }

        redeemScriptArea.clear();
        if(redeemScript != null) {
            appendScript(redeemScriptArea, redeemScript);
        } else {
            redeemScriptScroll.setDisable(true);
        }

        witnessesArea.clear();
        witnessScriptArea.clear();
        Script witnesses = null;
        Script witnessScript = null;

        if(txInput.hasWitness()) {
            List<ScriptChunk> witnessChunks = txInput.getWitness().asScriptChunks();
            if(witnessChunks.get(witnessChunks.size() - 1).isScript()) {
                witnesses = new Script(witnessChunks.subList(0, witnessChunks.size() - 1));
                witnessScript = witnessChunks.get(witnessChunks.size() - 1).getScript();
            } else {
                witnesses = new Script(witnessChunks);
            }
        } else if(psbtInput != null) {
            if(psbtInput.getFinalScriptWitness() != null) {
                List<ScriptChunk> witnessChunks = psbtInput.getFinalScriptWitness().asScriptChunks();
                if(witnessChunks.get(witnessChunks.size() - 1).isScript()) {
                    witnesses = new Script(witnessChunks.subList(0, witnessChunks.size() - 1));
                    witnessScript = witnessChunks.get(witnessChunks.size() - 1).getScript();
                } else {
                    witnesses = new Script(witnessChunks);
                }
            } else if(psbtInput.getWitnessScript() != null) {
                witnessScript = psbtInput.getWitnessScript();
            }
        }

        if(witnesses != null) {
            appendScript(witnessesArea, witnesses, null, witnessScript);
        } else {
            witnessesScroll.setDisable(true);
        }

        if(witnessScript != null) {
            appendScript(witnessScriptArea, witnessScript);
        } else {
            witnessScriptScroll.setDisable(true);
        }
    }

    private void initializeStatusFields(TransactionInput txInput) {
        Transaction transaction = inputForm.getTransaction();

        signatures.setText("Unknown");
        if(inputForm.getPsbtInput() != null) {
            PSBTInput psbtInput = inputForm.getPsbtInput();

            int reqSigs = -1;
            if((psbtInput.getNonWitnessUtxo() != null || psbtInput.getWitnessUtxo() != null) && psbtInput.getSigningScript() != null) {
                try {
                    reqSigs = psbtInput.getSigningScript().getNumRequiredSignatures();
                } catch (NonStandardScriptException e) {
                    //TODO: Handle unusual transaction sig
                }
            }

            int foundSigs = psbtInput.getPartialSignatures().size();
            if(psbtInput.getFinalScriptWitness() != null) {
                foundSigs = psbtInput.getFinalScriptWitness().getSignatures().size();
            } else if(psbtInput.getFinalScriptSig() != null) {
                foundSigs = psbtInput.getFinalScriptSig().getSignatures().size();
            }

            signatures.setText(foundSigs + "/" + (reqSigs < 0 ? "?" : reqSigs));
        }

        rbf.setSelected(txInput.isReplaceByFeeEnabled());
        rbf.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue) {
                if(txInput.isAbsoluteTimeLockDisabled()) {
                    locktimeToggleGroup.selectToggle(locktimeAbsoluteType);
                } else if(txInput.isAbsoluteTimeLocked()) {
                    txInput.setSequenceNumber(TransactionInput.SEQUENCE_RBF_ENABLED);
                    EventManager.get().notify(transaction);
                }
            } else {
                if(txInput.isAbsoluteTimeLocked()) {
                    txInput.setSequenceNumber(TransactionInput.SEQUENCE_LOCKTIME_DISABLED - 1);
                    EventManager.get().notify(transaction);
                } else if(txInput.isRelativeTimeLocked()) {
                    locktimeToggleGroup.selectToggle(locktimeAbsoluteType);
                }
            }
        });
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
                    EventManager.get().notify(transaction);
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
                    EventManager.get().notify(transaction);
                } else {
                    locktimeFieldset.getChildren().removeAll(locktimeRelativeField, locktimeAbsoluteField);
                    locktimeFieldset.getChildren().add(locktimeRelativeField);
                    if(locktimeRelativeCombo.getValue() == null) {
                        locktimeRelativeCombo.getSelectionModel().select(0);
                    } else {
                        setRelativeLocktime(txInput, transaction);
                    }
                    rbf.setSelected(true);
                }
            }
        });

        locktimeRelativeBlocks.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK, 0));
        locktimeRelativeBlocks.managedProperty().bind(locktimeRelativeBlocks.visibleProperty());
        locktimeRelativeSeconds.managedProperty().bind(locktimeRelativeSeconds.visibleProperty());
        locktimeRelativeCombo.getSelectionModel().selectedItemProperty().addListener((ov, old_toggle, new_toggle) -> {
            boolean blocks = locktimeRelativeCombo.getValue().equals("blocks");
            locktimeRelativeSeconds.setVisible(!blocks);
            locktimeRelativeBlocks.setVisible(blocks);
            setRelativeLocktime(txInput, transaction);
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
            setRelativeLocktime(txInput, transaction);
        });
        locktimeRelativeSeconds.valueProperty().addListener((obs, oldValue, newValue) -> {
            setRelativeLocktime(txInput, transaction);
        });
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

    private void setRelativeLocktime(TransactionInput txInput, Transaction transaction) {
        String relativeSelection = locktimeRelativeCombo.getValue();
        if(relativeSelection.equals("blocks")) {
            Integer value = locktimeRelativeBlocks.getValue();
            txInput.setSequenceNumber(value & TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK);
        } else {
            long value = locktimeRelativeSeconds.getValue().toSeconds() / TransactionInput.RELATIVE_TIMELOCK_SECONDS_INCREMENT;
            txInput.setSequenceNumber((value & TransactionInput.RELATIVE_TIMELOCK_VALUE_MASK) | TransactionInput.RELATIVE_TIMELOCK_TYPE_FLAG);
        }
        EventManager.get().notify(transaction);
    }

    public void setModel(InputForm form) {
        this.inputForm = form;
        initializeView();
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
}
