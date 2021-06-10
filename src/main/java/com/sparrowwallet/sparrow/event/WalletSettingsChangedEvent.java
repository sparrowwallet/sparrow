package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;

/**
 * This is the base class for events posted when a wallet's settings are changed
 * Do not listen for this event directly - listen for a subclass, for example KeystoreLabelsChangedEvent or WalletAddressesChangedEvent
 */
public class WalletSettingsChangedEvent extends WalletChangedEvent {
    private final Wallet pastWallet;
    private final String walletId;

    public WalletSettingsChangedEvent(Wallet wallet, Wallet pastWallet, String walletId) {
        super(wallet);
        this.pastWallet = pastWallet;
        this.walletId = walletId;
    }

    public Wallet getPastWallet() {
        return pastWallet;
    }

    public String getWalletId() {
        return walletId;
    }
}
