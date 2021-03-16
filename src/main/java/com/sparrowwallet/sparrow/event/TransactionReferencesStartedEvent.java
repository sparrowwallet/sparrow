package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionReferencesStartedEvent extends TransactionReferencesEvent {
    public TransactionReferencesStartedEvent(Transaction transaction) {
        super(transaction);
    }

    public TransactionReferencesStartedEvent(Transaction transaction, int pageStart, int pageEnd) {
        super(transaction, pageStart, pageEnd);
    }
}
