package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.transaction.TransactionData;

import java.io.File;

public class TransactionTabData extends TabData {
    private final File file;
    private final TransactionData transactionData;

    public TransactionTabData(TabType type, File file, TransactionData transactionData) {
        super(type);
        this.file = file;
        this.transactionData = transactionData;
    }

    public File getFile() {
        return file;
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
