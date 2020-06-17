package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.transaction.TransactionView;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;

public class ViewTransactionEvent {
    public final BlockTransaction blockTransaction;
    public final TransactionView initialView;
    public final Integer initialIndex;

    public ViewTransactionEvent(BlockTransaction blockTransaction) {
        this(blockTransaction, TransactionView.HEADERS, null);
    }

    public ViewTransactionEvent(BlockTransaction blockTransaction, HashIndexEntry hashIndexEntry) {
        this(blockTransaction, hashIndexEntry.getType().equals(HashIndexEntry.Type.INPUT) ? TransactionView.INPUT : TransactionView.OUTPUT, (int)hashIndexEntry.getHashIndex().getIndex());
    }

    public ViewTransactionEvent(BlockTransaction blockTransaction, TransactionView initialView, Integer initialIndex) {
        this.blockTransaction = blockTransaction;
        this.initialView = initialView;
        this.initialIndex = initialIndex;
    }

    public BlockTransaction getBlockTransaction() {
        return blockTransaction;
    }

    public TransactionView getInitialView() {
        return initialView;
    }

    public Integer getInitialIndex() {
        return initialIndex;
    }
}
