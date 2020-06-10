package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import javafx.scene.Node;

import java.io.IOException;

public abstract class TransactionForm {
    private Transaction transaction;
    private PSBT psbt;
    private BlockTransaction blockTransaction;

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

    public boolean isEditable() {
        return blockTransaction == null;
    }

    public abstract Node getContents() throws IOException;
}
