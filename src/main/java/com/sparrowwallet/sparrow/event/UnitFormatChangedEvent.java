package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.io.Config;

public class UnitFormatChangedEvent {
    private final UnitFormat unitFormat;

    public UnitFormatChangedEvent(UnitFormat unitFormat) {
        this.unitFormat = unitFormat;
    }

    public UnitFormat getUnitFormat() {
        return unitFormat;
    }

    public BitcoinUnit getBitcoinUnit() {
        return Config.get().getBitcoinUnit();
    }
}
