package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.BitcoinUnit;

public class BitcoinUnitChangedEvent {
    private final BitcoinUnit bitcoinUnit;

    public BitcoinUnitChangedEvent(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;
    }

    public BitcoinUnit getBitcoinUnit() {
        return bitcoinUnit;
    }
}
