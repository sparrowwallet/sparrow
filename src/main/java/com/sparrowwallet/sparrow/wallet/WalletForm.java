package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.storage.Storage;

import java.io.File;
import java.io.IOException;

public class WalletForm {
    private File walletFile;
    private Wallet oldWallet;
    private Wallet wallet;

    public WalletForm(File walletFile, Wallet currentWallet) {
        this.walletFile = walletFile;
        this.oldWallet = currentWallet;
        this.wallet = currentWallet.copy();
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void revert() {
        this.wallet = oldWallet.copy();
    }

    public void save() throws IOException {
        Storage.getStorage().storeWallet(walletFile, wallet);
        oldWallet = wallet.copy();
    }
}
