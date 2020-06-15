package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransaction;

public abstract class IndexedTransactionForm extends TransactionForm {
    private final int index;

    public IndexedTransactionForm(PSBT psbt, int index) {
        super(psbt);
        this.index = index;
    }

    public IndexedTransactionForm(BlockTransaction blockTransaction, int index) {
        super(blockTransaction);
        this.index = index;
    }

    public IndexedTransactionForm(Transaction transaction, int index) {
        super(transaction);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
