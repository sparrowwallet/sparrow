package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;

/**
 * This event is posted when a wallet's settings are changed (keystores, policy, script type).
 * This event marks a fundamental change that is used to update application level UI, clear node entry caches and similar. It should only be subscribed to by application-level classes.
 * It does not extend WalletChangedEvent since that is used to save the wallet, and the wallet is saved directly in SettingsController before this event is posted.
 * Note that all wallet detail controllers that share a WalletForm, that class posts WalletNodesChangedEvent once it has cleared it's entry caches.
 */
public class WalletSettingsChangedEvent {
    private final Wallet wallet;
    private final File walletFile;

    public WalletSettingsChangedEvent(Wallet wallet, File walletFile) {
        this.wallet = wallet;
        this.walletFile = walletFile;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public File getWalletFile() {
        return walletFile;
    }
}
