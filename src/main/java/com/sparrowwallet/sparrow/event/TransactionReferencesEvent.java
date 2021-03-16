package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionReferencesEvent extends PagedEvent {
    private final Transaction transaction;

    public TransactionReferencesEvent(Transaction transaction) {
        this(transaction, 0, 0);
    }

    public TransactionReferencesEvent(Transaction transaction, int pageStart, int pageEnd) {
        super(pageStart, pageEnd);
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
