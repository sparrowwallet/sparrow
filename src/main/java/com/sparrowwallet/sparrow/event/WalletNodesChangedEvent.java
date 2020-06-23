package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * This event is posted by WalletForm once it has received a WalletSettingsChangedEvent and cleared it's entry caches
 * The controllers in the wallet package listen to this event to update their views should a wallet's settings change
 */
public class WalletNodesChangedEvent extends WalletChangedEvent {
    public WalletNodesChangedEvent(Wallet wallet) {
        super(wallet);
    }
}
