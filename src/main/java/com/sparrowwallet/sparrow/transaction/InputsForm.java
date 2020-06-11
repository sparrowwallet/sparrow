package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputsForm extends TransactionForm {
    public InputsForm(PSBT psbt) {
        super(psbt);
    }

    public InputsForm(BlockTransaction blockTransaction) {
        super(blockTransaction);
    }

    public InputsForm(Transaction transaction) {
        super(transaction);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("inputs.fxml"));
        Node node = loader.load();
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
