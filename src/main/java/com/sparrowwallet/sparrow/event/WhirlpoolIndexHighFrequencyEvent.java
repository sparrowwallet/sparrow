package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WhirlpoolIndexHighFrequencyEvent {
    private final Wallet wallet;

    public WhirlpoolIndexHighFrequencyEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
