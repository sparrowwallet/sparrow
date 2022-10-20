package com.sparrowwallet.sparrow.terminal.wallet.table;

import com.google.common.base.Strings;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;

public class CoinTableCell extends TableCell {
    public static final int TRANSACTION_WIDTH = 24;
    public static final int UTXO_WIDTH = 18;

    private final boolean balance;

    public CoinTableCell(Entry entry, boolean balance) {
        super(entry);
        this.balance = balance;
    }

    @Override
    public String formatCell() {
        Long value = null;
        if(balance && entry instanceof TransactionEntry transactionEntry) {
            value = transactionEntry.getBalance();
        } else {
            value = entry.getValue();
        }

        if(value == null) {
            value = 0L;
        }

        BitcoinUnit unit = Config.get().getBitcoinUnit();
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = (value >= BitcoinUnit.getAutoThreshold() ? BitcoinUnit.BTC : BitcoinUnit.SATOSHIS);
        }

        UnitFormat format = Config.get().getUnitFormat();
        if(format == null) {
            format = UnitFormat.DOT;
        }

        String formattedValue = unit == BitcoinUnit.SATOSHIS ? format.formatSatsValue(value) : format.formatBtcValue(value);
        return Strings.padStart(formattedValue, getWidth(entry), ' ');
    }

    private int getWidth(Entry entry) {
        return entry instanceof TransactionEntry ? TRANSACTION_WIDTH : UTXO_WIDTH;
    }
}
