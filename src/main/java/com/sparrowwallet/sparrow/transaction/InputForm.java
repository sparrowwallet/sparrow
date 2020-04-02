package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.TransactionInput;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputForm extends TransactionForm {
    private TransactionInput transactionInput;

    public InputForm(TransactionInput transactionInput) {
        this.transactionInput = transactionInput;
    }

    public TransactionInput getTransactionInput() {
        return transactionInput;
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
