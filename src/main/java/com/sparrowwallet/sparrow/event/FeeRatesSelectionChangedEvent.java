package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.FeeRatesSelection;

public class FeeRatesSelectionChangedEvent {
    private final Wallet wallet;
    private final FeeRatesSelection feeRatesSelection;

    public FeeRatesSelectionChangedEvent(Wallet wallet, FeeRatesSelection feeRatesSelection) {
        this.wallet = wallet;
        this.feeRatesSelection = feeRatesSelection;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public FeeRatesSelection getFeeRateSelection() {
        return feeRatesSelection;
    }
}
