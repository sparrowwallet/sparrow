package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.sparrow.transaction.TransactionView;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import javafx.stage.Window;

public class ViewTransactionEvent {
    private final Window window;
    private final Transaction transaction;
    private final BlockTransaction blockTransaction;
    private final TransactionView initialView;
    private final Integer initialIndex;

    public ViewTransactionEvent(Window window, Transaction transaction) {
        this.window = window;
        this.transaction = transaction;
        this.blockTransaction = null;
        this.initialView = TransactionView.HEADERS;
        this.initialIndex = null;
    }

    public ViewTransactionEvent(Window window, BlockTransaction blockTransaction) {
        this(window, blockTransaction, TransactionView.HEADERS, null);
    }

    public ViewTransactionEvent(Window window, BlockTransaction blockTransaction, HashIndexEntry hashIndexEntry) {
        this(window, blockTransaction, hashIndexEntry.getType().equals(HashIndexEntry.Type.INPUT) ? TransactionView.INPUT : TransactionView.OUTPUT, (int)hashIndexEntry.getHashIndex().getIndex());
    }

    public ViewTransactionEvent(Window window, BlockTransaction blockTransaction, TransactionView initialView, Integer initialIndex) {
        this.window = window;
        this.transaction = blockTransaction.getTransaction();
        this.blockTransaction = blockTransaction;
        this.initialView = initialView;
        this.initialIndex = initialIndex;
    }

    public Window getWindow() {
        return window;
    }

    public Transaction getTransaction() {
        return transaction;
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
