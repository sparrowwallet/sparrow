package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionFetchFailedEvent extends TransactionReferencesFailedEvent {
    public TransactionFetchFailedEvent(Transaction transaction, Throwable exception) {
        super(transaction, exception);
    }

    public TransactionFetchFailedEvent(Transaction transaction, Throwable exception, int pageStart, int pageEnd) {
        super(transaction, exception, pageStart, pageEnd);
    }
}
