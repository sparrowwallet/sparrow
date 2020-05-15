package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;

public class TransactionTabData extends TabData {
    private Transaction transaction;

    public TransactionTabData(TabType type, Transaction transaction) {
        super(type);
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }
}
