package com.craigraw.sparrow.form;

import com.craigraw.drongo.protocol.Transaction;
import com.craigraw.drongo.psbt.PSBT;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputsForm extends Form {
    private Transaction transaction;
    private PSBT psbt;

    public InputsForm(Transaction transaction, PSBT psbt) {
        this.transaction = transaction;
        this.psbt = psbt;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public PSBT getPsbt() {
        return psbt;
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
