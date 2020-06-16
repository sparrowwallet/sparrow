package com.sparrowwallet.sparrow.transaction;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputsForm extends TransactionForm {
    public InputsForm(TransactionData txdata) {
        super(txdata);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("inputs.fxml"));
        Node node = loader.load();
        node.setUserData(this);
        InputsController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    @Override
    public TransactionView getView() {
        return TransactionView.INPUTS;
    }

    public String toString() {
        return "Inputs";
    }
}
