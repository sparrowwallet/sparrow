package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.TransactionTabData;

import java.util.List;

public class TransactionTabsClosedEvent {
    private final List<TransactionTabData> closedTransactionTabData;

    public TransactionTabsClosedEvent(List<TransactionTabData> closedTransactionTabData) {
        this.closedTransactionTabData = closedTransactionTabData;
    }

    public List<TransactionTabData> getClosedTransactionTabData() {
        return closedTransactionTabData;
    }
}
