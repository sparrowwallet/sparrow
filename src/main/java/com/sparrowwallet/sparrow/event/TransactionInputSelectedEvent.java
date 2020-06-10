package com.sparrowwallet.sparrow.event;

public class TransactionInputSelectedEvent {
    private final long index;

    public TransactionInputSelectedEvent(long index) {
        this.index = index;
    }

    public long getIndex() {
        return index;
    }
}
