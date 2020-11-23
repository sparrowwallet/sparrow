package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.wallet.FeeRatesSelection;

public class FeeRatesSelectionChangedEvent {
    private final FeeRatesSelection feeRatesSelection;

    public FeeRatesSelectionChangedEvent(FeeRatesSelection feeRatesSelection) {
        this.feeRatesSelection = feeRatesSelection;
    }

    public FeeRatesSelection getFeeRateSelection() {
        return feeRatesSelection;
    }
}
