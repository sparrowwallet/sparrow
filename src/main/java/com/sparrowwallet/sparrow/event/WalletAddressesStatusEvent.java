package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * Used to indicate that the display configuration of wallet addresses has been updated
 */
public class WalletAddressesStatusEvent {
    private final Wallet wallet;

    public WalletAddressesStatusEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
