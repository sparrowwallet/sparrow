package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * This event is posted by WalletForm once it has received a WalletSettingsChangedEvent and cleared it's entry caches
 * It does not extend WalletChangedEvent for the same reason WalletSettingsChangedEvent does not - it does not want to trigger a wallet save.
 */
public class WalletNodesChangedEvent {
    private final Wallet wallet;

    public WalletNodesChangedEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
