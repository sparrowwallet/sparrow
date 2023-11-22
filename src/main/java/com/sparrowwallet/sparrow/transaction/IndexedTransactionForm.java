package com.sparrowwallet.sparrow.transaction;

public abstract class IndexedTransactionForm extends TransactionForm {
    private int index;

    public IndexedTransactionForm(TransactionData txdata, int index) {
        super(txdata);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
