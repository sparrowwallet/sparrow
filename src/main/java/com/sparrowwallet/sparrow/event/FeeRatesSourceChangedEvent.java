package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.FeeRatesSource;

public class FeeRatesSourceChangedEvent {
    private final FeeRatesSource feeRatesSource;

    public FeeRatesSourceChangedEvent(FeeRatesSource feeRatesSource) {
        this.feeRatesSource = feeRatesSource;
    }

    public FeeRatesSource getFeeRateSource() {
        return feeRatesSource;
    }
}
