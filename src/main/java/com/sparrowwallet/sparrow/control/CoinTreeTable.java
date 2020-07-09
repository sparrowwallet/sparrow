package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.scene.control.TreeTableView;

public class CoinTreeTable extends TreeTableView<Entry> {
    private BitcoinUnit bitcoinUnit;

    public BitcoinUnit getBitcoinUnit() {
        return bitcoinUnit;
    }

    public void setBitcoinUnit(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;
    }

    public void setBitcoinUnit(Wallet wallet) {
        setBitcoinUnit(wallet, Config.get().getBitcoinUnit());
    }

    public void setBitcoinUnit(Wallet wallet, BitcoinUnit unit) {
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = wallet.getAutoUnit();
        }

        boolean changed = (bitcoinUnit != unit);
        this.bitcoinUnit = unit;

        if(changed && !getChildren().isEmpty()) {
            refresh();
        }
    }
}
