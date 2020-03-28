package com.craigraw.sparrow.form;

import com.craigraw.drongo.protocol.Transaction;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputsForm extends Form {
    private Transaction transaction;

    public InputsForm(Transaction transaction) {
        this.transaction = transaction;
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
