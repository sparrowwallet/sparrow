package com.sparrowwallet.sparrow.terminal.wallet.table;

import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;

public class OutputTableCell extends TableCell {
    public static final int WIDTH = 16;

    public OutputTableCell(Entry entry) {
        super(entry);
    }

    @Override
    public String formatCell() {
        if(entry instanceof UtxoEntry utxoEntry) {
            return utxoEntry.getDescription();
        }

        return "";
    }
}
