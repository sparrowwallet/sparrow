package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class OutputsForm extends TransactionForm {
    public OutputsForm(PSBT psbt) {
        super(psbt);
    }

    public OutputsForm(BlockTransaction blockTransaction) {
        super(blockTransaction);
    }

    public OutputsForm(Transaction transaction) {
        super(transaction);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("outputs.fxml"));
        Node node = loader.load();
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
