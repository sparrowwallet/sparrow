package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionChangedEvent {
    private Transaction transaction;

    public TransactionChangedEvent(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
