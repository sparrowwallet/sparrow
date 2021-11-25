package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class SorobanInitiatedEvent {
    private Wallet wallet;

    public SorobanInitiatedEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
