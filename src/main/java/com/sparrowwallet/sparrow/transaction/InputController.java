package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.fxmisc.richtext.CodeArea;
import tornadofx.control.Fieldset;

import java.net.URL;
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
    private CodeArea scriptSig;

    @FXML
    private CodeArea witness;

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

        scriptSig.clear();
        appendScript(scriptSig, txInput.getScriptSig().toDisplayString());

        witness.clear();
        appendScript(witness, txInput.getWitness().toString());
    }

    public void setModel(InputForm form) {
        this.inputForm = form;
        initializeView();
    }
}
