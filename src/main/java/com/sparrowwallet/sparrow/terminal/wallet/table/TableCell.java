package com.sparrowwallet.sparrow.terminal.wallet.table;

import com.sparrowwallet.sparrow.wallet.Entry;

public abstract class TableCell {
    protected final Entry entry;

    public TableCell(Entry entry) {
        this.entry = entry;
    }

    public Entry getEntry() {
        return entry;
    }

    public abstract String formatCell();
}
