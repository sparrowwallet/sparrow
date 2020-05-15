package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;

public class WalletChangedEvent {
    private final Wallet wallet;
    private final File walletFile;

    public WalletChangedEvent(Wallet wallet, File walletFile) {
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
