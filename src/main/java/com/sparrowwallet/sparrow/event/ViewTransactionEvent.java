package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;

public class ViewTransactionEvent {
    public final BlockTransaction blockTransaction;
    public final HashIndexEntry hashIndexEntry;

    public ViewTransactionEvent(BlockTransaction blockTransaction) {
        this(blockTransaction, null);
    }

    public ViewTransactionEvent(BlockTransaction blockTransaction, HashIndexEntry hashIndexEntry) {
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
