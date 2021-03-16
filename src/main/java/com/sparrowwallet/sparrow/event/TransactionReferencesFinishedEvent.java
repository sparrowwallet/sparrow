package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;

public class TransactionReferencesFinishedEvent extends TransactionReferencesEvent {
    private final BlockTransaction blockTransaction;

    public TransactionReferencesFinishedEvent(Transaction transaction, BlockTransaction blockTransaction) {
        super(transaction);
        this.blockTransaction = blockTransaction;
    }

    public TransactionReferencesFinishedEvent(Transaction transaction, BlockTransaction blockTransaction, int pageStart, int pageEnd) {
        super(transaction, pageStart, pageEnd);
        this.blockTransaction = blockTransaction;
    }

    public BlockTransaction getBlockTransaction() {
        return blockTransaction;
    }
}
