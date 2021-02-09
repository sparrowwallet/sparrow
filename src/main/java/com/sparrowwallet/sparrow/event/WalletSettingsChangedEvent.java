package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;

/**
 * This event is posted when a wallet's settings are changed (keystores, policy, script type).
 * This event marks a fundamental change that is used to update application level UI, clear node entry caches and similar. It should only be subscribed to by application-level classes.
 * Note that WalletForm does not listen to this event to save the wallet, since the wallet is foreground saved directly in SettingsController before this event is posted.
 * This is because any failure in saving the wallet must be immediately reported to the user.
 * Note that all wallet detail controllers that share a WalletForm, and that class posts WalletNodesChangedEvent once it has cleared it's entry caches.
 */
public class WalletSettingsChangedEvent extends WalletChangedEvent {
    private final Wallet pastWallet;
    private final File walletFile;

    public WalletSettingsChangedEvent(Wallet wallet, Wallet pastWallet, File walletFile) {
        super(wallet);
        this.pastWallet = pastWallet;
        this.walletFile = walletFile;
    }

    public Wallet getPastWallet() {
        return pastWallet;
    }

    public File getWalletFile() {
        return walletFile;
    }
}
