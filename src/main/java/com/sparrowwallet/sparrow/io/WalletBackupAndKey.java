package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Map;

public class WalletBackupAndKey {
    private final Wallet wallet;
    private final Wallet backupWallet;
    private final ECKey encryptionKey;
    private final Key key;
    private final Map<Storage, WalletBackupAndKey> childWallets;

    public WalletBackupAndKey(Wallet wallet, Wallet backupWallet, ECKey encryptionKey, AsymmetricKeyDeriver keyDeriver, Map<Storage, WalletBackupAndKey> childWallets) {
        this.wallet = wallet;
        this.backupWallet = backupWallet;
        this.encryptionKey = encryptionKey;
        this.key = encryptionKey == null ? null : new Key(encryptionKey.getPrivKeyBytes(), keyDeriver.getSalt(), EncryptionType.Deriver.ARGON2);
        this.childWallets = childWallets;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Wallet getBackupWallet() {
        return backupWallet;
    }

    public ECKey getEncryptionKey() {
        return encryptionKey;
    }

    public Key getKey() {
        return key;
    }

    public Map<Storage, WalletBackupAndKey> getChildWallets() {
        return childWallets;
    }

    public void clear() {
        if(encryptionKey != null) {
            encryptionKey.clear();
        }
        if(key != null) {
            key.clear();
        }
    }
}
