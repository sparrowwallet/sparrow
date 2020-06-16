package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class OutputsForm extends TransactionForm {
    public OutputsForm(TransactionData txdata) {
        super(txdata);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("outputs.fxml"));
        Node node = loader.load();
        node.setUserData(this);
        OutputsController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    @Override
    public TransactionView getView() {
        return TransactionView.OUTPUTS;
    }

    public String toString() {
        return "Outputs";
    }
}
