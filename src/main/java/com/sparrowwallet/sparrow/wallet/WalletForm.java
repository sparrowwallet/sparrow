package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.crypto.ECIESKeyCrypter;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;

import java.io.File;
import java.io.IOException;

public class WalletForm {
    public static final ECKey NO_PASSWORD_KEY = ECKey.fromPublicOnly(ECIESKeyCrypter.deriveECKey(""));

    private final File walletFile;
    private ECKey encryptionPubKey;
    private Wallet oldWallet;
    private Wallet wallet;

    public WalletForm(File walletFile, ECKey encryptionPubKey, Wallet currentWallet) {
        this.walletFile = walletFile;
        this.encryptionPubKey = encryptionPubKey;
        this.oldWallet = currentWallet;
        this.wallet = currentWallet.copy();
    }

    public Wallet getWallet() {
        return wallet;
    }

    public File getWalletFile() {
        return walletFile;
    }

    public ECKey getEncryptionPubKey() {
        return encryptionPubKey;
    }

    public void setEncryptionPubKey(ECKey encryptionPubKey) {
        this.encryptionPubKey = encryptionPubKey;
    }

    public void revert() {
        this.wallet = oldWallet.copy();
    }

    public void save() throws IOException {
        if(encryptionPubKey.equals(NO_PASSWORD_KEY)) {
            Storage.getStorage().storeWallet(walletFile, wallet);
        } else {
            Storage.getStorage().storeWallet(walletFile, encryptionPubKey, wallet);
        }

        oldWallet = wallet.copy();
    }
}
