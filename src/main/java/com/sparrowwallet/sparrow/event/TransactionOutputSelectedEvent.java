package com.sparrowwallet.sparrow.event;

public class TransactionOutputSelectedEvent {
    private final long index;

    public TransactionOutputSelectedEvent(long index) {
        this.index = index;
    }

    public long getIndex() {
        return index;
    }
}
