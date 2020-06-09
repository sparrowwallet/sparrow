package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;

public class TransactionViewEvent {
    public final BlockTransaction blockTransaction;
    public final HashIndexEntry hashIndexEntry;

    public TransactionViewEvent(BlockTransaction blockTransaction) {
        this(blockTransaction, null);
    }

    public TransactionViewEvent(BlockTransaction blockTransaction, HashIndexEntry hashIndexEntry) {
        this.blockTransaction = blockTransaction;
        this.hashIndexEntry = hashIndexEntry;
    }

    public BlockTransaction getBlockTransaction() {
        return blockTransaction;
    }

    public HashIndexEntry getHashIndexEntry() {
        return hashIndexEntry;
    }
}
