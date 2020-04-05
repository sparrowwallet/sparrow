package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputsForm extends TransactionForm {
    public InputsForm(PSBT psbt) {
        super(psbt);
    }

    public InputsForm(Transaction transaction) {
        super(transaction);
    }

    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("inputs.fxml"));
        Node node = loader.load();
        InputsController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    public String toString() {
        return "Inputs";
    }
}
