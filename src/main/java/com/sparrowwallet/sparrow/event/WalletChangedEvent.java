package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * The base class for all wallet events that should also trigger saving of the wallet
 */
public class WalletChangedEvent {
    private final Wallet wallet;

    public WalletChangedEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
