package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.sparrow.EventManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.fxmisc.richtext.CodeArea;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.net.URL;
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
    private Field locktimeNoneField;

    @FXML
    private Field locktimeAbsoluteField;

    @FXML
    private Field locktimeRelativeField;

    @FXML
    private Spinner<Integer> locktimeNone;

    @FXML
    private TextField locktimeAbsolute;

    @FXML
    private Spinner<Integer> locktimeRelative;

    @FXML
    private ComboBox<String> locktimeRelativeCombo;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void initializeView() {
        TransactionInput txInput = inputForm.getTransactionInput();
        PSBTInput psbtInput = inputForm.getPsbtInput();

        inputFieldset.setText("Input #" + txInput.getIndex());
        outpoint.setText(txInput.getOutpoint().getHash().toString() + ":" + txInput.getOutpoint().getIndex());

        //TODO: Enable select outpoint when wallet present
        outpointSelect.setDisable(true);
        initializeScriptFields(txInput);

        initializeLocktimeFields(txInput);
    }

    private void initializeScriptFields(TransactionInput txInput) {
        //TODO: Is this safe?
        Script redeemScript = txInput.getScriptSig().getFirstNestedScript();

        scriptSigArea.clear();
        appendScript(scriptSigArea, txInput.getScriptSig(), redeemScript, null);

        redeemScriptArea.clear();
        if(redeemScript != null) {
            appendScript(redeemScriptArea, redeemScript);
        } else {
            redeemScriptScroll.setDisable(true);
        }

        witnessesArea.clear();
        witnessScriptArea.clear();
        if(txInput.hasWitness()) {
            List<ScriptChunk> witnessChunks = txInput.getWitness().asScriptChunks();
            if(witnessChunks.get(witnessChunks.size() - 1).isScript()) {
                Script witnessScript = new Script(witnessChunks.get(witnessChunks.size() - 1).getData());
                appendScript(witnessesArea, new Script(witnessChunks.subList(0, witnessChunks.size() - 1)), null, witnessScript);
                appendScript(witnessScriptArea, witnessScript);
            } else {
                appendScript(witnessesArea, new Script(witnessChunks));
                witnessScriptScroll.setDisable(true);
            }
        } else {
            witnessesScroll.setDisable(true);
            witnessScriptScroll.setDisable(true);
        }
    }

    private void initializeLocktimeFields(TransactionInput txInput) {
        Transaction transaction = inputForm.getTransaction();
        locktimeToggleGroup.selectedToggleProperty().addListener((ov, old_toggle, new_toggle) -> {
            if(locktimeToggleGroup.getSelectedToggle() != null) {
                String selection = locktimeToggleGroup.getSelectedToggle().getUserData().toString();
                if(selection.equals("none")) {
                    locktimeFieldset.getChildren().removeAll(locktimeRelativeField, locktimeAbsoluteField, locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeNoneField);
                    txInput.setSequenceNumber(TransactionInput.SEQUENCE_LOCKTIME_DISABLED);
                    EventManager.get().notify(transaction);
                } else if(selection.equals("absolute")) {
                    locktimeFieldset.getChildren().removeAll(locktimeRelativeField, locktimeAbsoluteField, locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeAbsoluteField);
                    long locktime = transaction.getLocktime();
                    if(locktime < Transaction.MAX_BLOCK_LOCKTIME) {
                        locktimeAbsoluteField.setText("Block:");
                        locktimeAbsolute.setText(Long.toString(locktime));
                    } else {
                        locktimeAbsoluteField.setText("Date:");
                        LocalDateTime localDateTime = Instant.ofEpochSecond(locktime).atZone(ZoneId.systemDefault()).toLocalDateTime();
                        locktimeAbsolute.setText(DateTimeFormatter.ofPattern(HeadersController.LOCKTIME_DATE_FORMAT).format(localDateTime));
                    }
                    //TODO: Check RBF field and set appropriately
                    txInput.setSequenceNumber(TransactionInput.SEQUENCE_LOCKTIME_DISABLED - 1);
                    EventManager.get().notify(transaction);
                } else {
                    locktimeFieldset.getChildren().removeAll(locktimeRelativeField, locktimeAbsoluteField, locktimeNoneField);
                    locktimeFieldset.getChildren().add(locktimeRelativeField);
                    setRelativeLocktime(txInput, transaction, locktimeRelative.getValue());
                }
            }
        });

        locktimeNone.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)Transaction.MAX_BLOCK_LOCKTIME-1, (int)transaction.getLocktime()));
        locktimeRelativeCombo.getSelectionModel().select(0);
        locktimeRelative.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, (int)TransactionInput.MAX_RELATIVE_TIMELOCK_IN_BLOCKS, 0));
        if(txInput.isAbsoluteTimeLockDisabled()) {
            locktimeToggleGroup.selectToggle(locktimeNoneType);
        } else if(txInput.isAbsoluteTimeLocked()) {
            locktimeToggleGroup.selectToggle(locktimeAbsoluteType);
        } else {
            locktimeRelative.valueFactoryProperty().get().setValue((int)txInput.getRelativeLocktime());
            if(txInput.isRelativeTimeLockedInBlocks()) {
                locktimeRelativeCombo.getSelectionModel().select(0);
            } else {
                locktimeRelativeCombo.getSelectionModel().select(1);
            }
            locktimeToggleGroup.selectToggle(locktimeRelativeType);
        }

        locktimeRelative.valueProperty().addListener((obs, oldValue, newValue) -> {
            setRelativeLocktime(txInput, transaction, newValue);
        });

        locktimeRelativeCombo.getSelectionModel().selectedItemProperty().addListener((ov, old_toggle, new_toggle) -> {
            setRelativeLocktime(txInput, transaction, locktimeRelative.getValue());
        });
    }

    private void setRelativeLocktime(TransactionInput txInput, Transaction transaction, Integer value) {
        String relativeSelection = locktimeRelativeCombo.getValue();
        if (relativeSelection.equals("blocks")) {
            txInput.setSequenceNumber(value & 0xFFFF);
        } else {
            txInput.setSequenceNumber((value & 0xFFFF) | 0x400000);
        }
        EventManager.get().notify(transaction);
    }

    public void setModel(InputForm form) {
        this.inputForm = form;
        initializeView();
    }
}
