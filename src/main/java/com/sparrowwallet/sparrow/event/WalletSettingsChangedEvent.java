package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;

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
