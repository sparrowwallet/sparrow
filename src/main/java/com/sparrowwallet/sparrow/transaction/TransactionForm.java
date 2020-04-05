package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import javafx.scene.Node;

import java.io.IOException;

public abstract class TransactionForm {
    private Transaction transaction;
    private PSBT psbt;

    public TransactionForm(PSBT psbt) {
        this.transaction = psbt.getTransaction();
        this.psbt = psbt;
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

    public abstract Node getContents() throws IOException;
}
