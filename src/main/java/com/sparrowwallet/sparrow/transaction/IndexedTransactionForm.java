package com.sparrowwallet.sparrow.transaction;

public abstract class IndexedTransactionForm extends TransactionForm {
    private final int index;

    public IndexedTransactionForm(TransactionData txdata, int index) {
        super(txdata);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
