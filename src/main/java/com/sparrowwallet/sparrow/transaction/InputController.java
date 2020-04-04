package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.fxmisc.richtext.CodeArea;
import tornadofx.control.Fieldset;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.net.URL;
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

    public void setModel(InputForm form) {
        this.inputForm = form;
        initializeView();
    }
}
