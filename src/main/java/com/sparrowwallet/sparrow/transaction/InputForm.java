package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputForm extends TransactionForm {
    private TransactionInput transactionInput;
    private PSBTInput psbtInput;

    public InputForm(TransactionInput transactionInput, PSBTInput psbtInput) {
        this.transactionInput = transactionInput;
        this.psbtInput = psbtInput;
    }

    public TransactionInput getTransactionInput() {
        return transactionInput;
    }

    public PSBTInput getPsbtInput() {
        return psbtInput;
    }

    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("input.fxml"));
        Node node = loader.load();
        InputController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    public String toString() {
        return "Input #" + transactionInput.getIndex();
    }
}
