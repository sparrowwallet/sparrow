package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionLocktimeChangedEvent extends TransactionChangedEvent {
    public TransactionLocktimeChangedEvent(Transaction transaction) {
        super(transaction);
    }
}
