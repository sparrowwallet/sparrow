package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionReferencesFailedEvent extends TransactionReferencesEvent {
    private final Throwable exception;

    public TransactionReferencesFailedEvent(Transaction transaction, Throwable exception) {
        super(transaction);
        this.exception = exception;
    }

    public TransactionReferencesFailedEvent(Transaction transaction, Throwable exception, int pageStart, int pageEnd) {
        super(transaction, pageStart, pageEnd);
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }
}
