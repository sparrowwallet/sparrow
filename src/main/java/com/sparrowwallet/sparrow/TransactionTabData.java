package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.transaction.TransactionData;

public class TransactionTabData extends TabData {
    private final TransactionData transactionData;

    public TransactionTabData(TabType type, TransactionData transactionData) {
        super(type);
        this.transactionData = transactionData;
    }

    public TransactionData getTransactionData() {
        return transactionData;
    }

    public Transaction getTransaction() {
        return transactionData.getTransaction();
    }

    public PSBT getPsbt() {
        return transactionData.getPsbt();
    }
}
