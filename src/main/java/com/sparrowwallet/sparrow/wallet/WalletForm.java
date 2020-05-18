package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.Pbkdf2KeyDeriver;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

import java.io.File;
import java.io.IOException;

public class WalletForm {
    private final Storage storage;
    private Wallet oldWallet;
    private Wallet wallet;

    public WalletForm(Storage storage, Wallet currentWallet) {
        this.storage = storage;
        this.oldWallet = currentWallet;
        this.wallet = currentWallet.copy();
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Storage getStorage() {
        return storage;
    }

    public File getWalletFile() {
        return storage.getWalletFile();
    }

    public void revert() {
        this.wallet = oldWallet.copy();
    }

    public void save() throws IOException {
        storage.storeWallet(wallet);
        oldWallet = wallet.copy();
    }
}
