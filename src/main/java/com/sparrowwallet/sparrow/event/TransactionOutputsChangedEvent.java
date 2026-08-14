package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionOutputsChangedEvent extends TransactionChangedEvent {
    public TransactionOutputsChangedEvent(Transaction transaction) {
        super(transaction);
    }
}
