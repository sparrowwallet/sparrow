package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.address.Address;

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

    public abstract Address getAddress();
}
