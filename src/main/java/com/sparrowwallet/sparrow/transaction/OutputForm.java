package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

public class OutputForm extends IndexedTransactionForm {
    private final TransactionOutput transactionOutput;
    private PSBTOutput psbtOutput;

    public OutputForm(PSBT psbt, PSBTOutput psbtOutput) {
        super(psbt, psbt.getPsbtOutputs().indexOf(psbtOutput));
        this.transactionOutput = psbt.getTransaction().getOutputs().get(psbt.getPsbtOutputs().indexOf(psbtOutput));
        this.psbtOutput = psbtOutput;
    }

    public OutputForm(BlockTransaction blockTransaction, TransactionOutput transactionOutput) {
        super(blockTransaction, blockTransaction.getTransaction().getOutputs().indexOf(transactionOutput));
        this.transactionOutput = transactionOutput;
    }

    public OutputForm(Transaction transaction, TransactionOutput transactionOutput) {
        super(transaction, transaction.getOutputs().indexOf(transactionOutput));
        this.transactionOutput = transactionOutput;
    }

    public TransactionOutput getTransactionOutput() {
        return transactionOutput;
    }

    public PSBTOutput getPsbtOutput() {
        return psbtOutput;
    }

    public int getTransactionOutputIndex() {
        return getTransaction().getOutputs().indexOf(transactionOutput);
    }

    @Override
    public Node getContents() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("output.fxml"));
        Node node = loader.load();
        OutputController controller = loader.getController();
        controller.setModel(this);
        return node;
    }

    @Override
    public TransactionView getView() {
        return TransactionView.OUTPUT;
    }

    public String toString() {
        return "Output #" + transactionOutput.getIndex();
    }
}
