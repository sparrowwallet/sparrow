package com.sparrowwallet.sparrow.terminal.wallet.table;

import com.google.common.base.Strings;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;

public class MixTableCell extends TableCell {
    public static final int WIDTH = 18;

    public MixTableCell(Entry entry) {
        super(entry);
    }

    @Override
    public String formatCell() {
        if(entry instanceof UtxoEntry utxoEntry) {
            return getMixCountOnly(utxoEntry.mixStatusProperty().get());
        }

        return "";
    }

    private String getMixCountOnly(UtxoEntry.MixStatus mixStatus) {
        return Strings.padStart(Integer.toString(mixStatus == null ? 0 : mixStatus.getMixesDone()), WIDTH, ' ');
    }
}
