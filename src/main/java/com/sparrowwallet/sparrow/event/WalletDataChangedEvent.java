package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * Indicates that the internal data (non-settings) of the wallet has changed, either from a blockchain update or entry label change etc.
 * Used to trigger a background save of the wallet
 */
public class WalletDataChangedEvent extends WalletChangedEvent {
    public WalletDataChangedEvent(Wallet wallet) {
        super(wallet);
    }
}
