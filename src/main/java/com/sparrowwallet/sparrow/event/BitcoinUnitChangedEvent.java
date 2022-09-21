package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.io.Config;

public class BitcoinUnitChangedEvent extends UnitFormatChangedEvent {
    private final BitcoinUnit bitcoinUnit;

    public BitcoinUnitChangedEvent(BitcoinUnit bitcoinUnit) {
        super(Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat());
        this.bitcoinUnit = bitcoinUnit;
    }

    public BitcoinUnitChangedEvent(UnitFormat unitFormat, BitcoinUnit bitcoinUnit) {
        super(unitFormat);
        this.bitcoinUnit = bitcoinUnit;
    }

    public BitcoinUnit getBitcoinUnit() {
        return bitcoinUnit;
    }
}
