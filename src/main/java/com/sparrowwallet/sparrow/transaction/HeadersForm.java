package com.sparrowwallet.sparrow.transaction;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class HeadersForm extends TransactionForm {
    public HeadersForm(TransactionData txdata) {
        super(txdata);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("headers.fxml"));
        Node node = loader.load();
        HeadersController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    @Override
    public TransactionView getView() {
        return TransactionView.HEADERS;
    }

    public String toString() {
        return "Tx [" + getTransaction().calculateTxId(false).toString().substring(0, 6) + "]";
    }
}
