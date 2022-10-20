package com.sparrowwallet.sparrow.terminal.wallet.table;

import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;

public class AddressTableCell extends TableCell {
    public static final int ADDRESS_MIN_WIDTH = 52;
    public static final int UTXO_WIDTH = 18;

    public AddressTableCell(Entry entry) {
        super(entry);
    }

    @Override
    public String formatCell() {
        if(entry instanceof NodeEntry nodeEntry) {
            return nodeEntry.getAddress().toString();
        } else if(entry instanceof UtxoEntry utxoEntry) {
            return utxoEntry.getNode().getAddress().toString().substring(0, 10) + "..";
        }

        return "";
    }
}
