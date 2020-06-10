package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class InputForm extends TransactionForm {
    private TransactionInput transactionInput;
    private PSBTInput psbtInput;

    public InputForm(PSBT psbt, PSBTInput psbtInput) {
        super(psbt);
        this.transactionInput = psbt.getTransaction().getInputs().get(psbt.getPsbtInputs().indexOf(psbtInput));
        this.psbtInput = psbtInput;
    }

    public InputForm(BlockTransaction blockTransaction, TransactionInput transactionInput) {
        super(blockTransaction);
        this.transactionInput = transactionInput;
    }

    public InputForm(Transaction transaction, TransactionInput transactionInput) {
        super(transaction);
        this.transactionInput = transactionInput;
    }

    public TransactionInput getTransactionInput() {
        return transactionInput;
    }

    public PSBTInput getPsbtInput() {
        return psbtInput;
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
