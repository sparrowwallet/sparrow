package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;

public class TransactionTabData extends TabData {
    private final Transaction transaction;
    private final PSBT psbt;

    public TransactionTabData(TabType type, Transaction transaction) {
        this(type, transaction, null);
    }

    public TransactionTabData(TabType type, Transaction transaction, PSBT psbt) {
        super(type);
        this.transaction = transaction;
        this.psbt = psbt;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public PSBT getPsbt() {
        return psbt;
    }
}
