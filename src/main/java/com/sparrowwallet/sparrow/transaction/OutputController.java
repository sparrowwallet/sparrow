package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.TransactionOutput;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import org.fxmisc.richtext.CodeArea;
import tornadofx.control.Fieldset;

import java.net.URL;
import java.util.ResourceBundle;

public class OutputController extends TransactionFormController implements Initializable {
    private OutputForm outputForm;

    @FXML
    private Fieldset outputFieldset;

    @FXML
    private TextField value;

    @FXML
    private CodeArea scriptPubKeyArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void initializeView() {
        TransactionOutput txOutput = outputForm.getTransactionOutput();

        outputFieldset.setText("Output #" + txOutput.getIndex());
        value.setText(txOutput.getValue() + " sats");

        scriptPubKeyArea.clear();
        appendScript(scriptPubKeyArea, txOutput.getScript(), null, null);
    }

    public void setModel(OutputForm form) {
        this.outputForm = form;
        initializeView();
    }
}
