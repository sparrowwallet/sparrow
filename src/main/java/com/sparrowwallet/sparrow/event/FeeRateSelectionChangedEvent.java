package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.wallet.FeeRateSelection;

public class FeeRateSelectionChangedEvent {
    private final FeeRateSelection feeRateSelection;

    public FeeRateSelectionChangedEvent(FeeRateSelection feeRateSelection) {
        this.feeRateSelection = feeRateSelection;
    }

    public FeeRateSelection getFeeRateSelection() {
        return feeRateSelection;
    }
}
