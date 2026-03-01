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
            String description = utxoEntry.getDescription();
            int maxLength = WIDTH - 2;
            if (description.length() > maxLength) {
                return description.substring(0, maxLength - 2) + "..";
            }

            return description;
        }

        return "";
    }
}
