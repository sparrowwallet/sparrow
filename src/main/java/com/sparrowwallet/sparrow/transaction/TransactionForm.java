package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.scene.Node;

import java.io.IOException;
import java.util.Map;

public abstract class TransactionForm {
    private final Transaction transaction;
    private PSBT psbt;
    private BlockTransaction blockTransaction;
    private Map<Sha256Hash, BlockTransaction> inputTransactions;

    public TransactionForm(PSBT psbt) {
        this.transaction = psbt.getTransaction();
        this.psbt = psbt;
    }

    public TransactionForm(BlockTransaction blockTransaction) {
        this.transaction = blockTransaction.getTransaction();
        this.blockTransaction = blockTransaction;
    }

    public TransactionForm(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public PSBT getPsbt() {
        return psbt;
    }

    public BlockTransaction getBlockTransaction() {
        return blockTransaction;
    }

    public void setBlockTransaction(BlockTransaction blockTransaction) {
        this.blockTransaction = blockTransaction;
    }

    public Map<Sha256Hash, BlockTransaction> getInputTransactions() {
        return inputTransactions;
    }

    public void setInputTransactions(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        this.inputTransactions = inputTransactions;
    }

    public boolean isEditable() {
        return blockTransaction == null;
    }

    public abstract Node getContents() throws IOException;

    public abstract TransactionView getView();
}
