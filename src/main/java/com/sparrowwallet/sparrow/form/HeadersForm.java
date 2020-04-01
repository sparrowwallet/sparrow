package com.sparrowwallet.sparrow.form;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class HeadersForm extends Form {
    private Transaction transaction;
    private PSBT psbt;

    public HeadersForm(Transaction transaction, PSBT psbt) {
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
        FXMLLoader loader = new FXMLLoader(getClass().getResource("headers.fxml"));
        Node node = loader.load();
        HeadersController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    public String toString() {
        return "Tx [" + transaction.calculateTxId(false).toString().substring(0, 6) + "]";
    }
}
