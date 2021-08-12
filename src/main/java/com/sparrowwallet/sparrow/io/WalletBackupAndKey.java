package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Map;

public class WalletBackupAndKey implements Comparable<WalletBackupAndKey> {
    private final Wallet wallet;
    private final Wallet backupWallet;
    private final ECKey encryptionKey;
    private final Key key;
    private final Map<WalletBackupAndKey, Storage> childWallets;

    public WalletBackupAndKey(Wallet wallet, Wallet backupWallet, ECKey encryptionKey, AsymmetricKeyDeriver keyDeriver, Map<WalletBackupAndKey, Storage> childWallets) {
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

    public Map<WalletBackupAndKey, Storage> getChildWallets() {
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

    @Override
    public int compareTo(WalletBackupAndKey other) {
        if(wallet.getStandardAccountType() != null && other.wallet.getStandardAccountType() != null) {
            return wallet.getStandardAccountType().ordinal() - other.wallet.getStandardAccountType().ordinal();
        }

        return wallet.getAccountIndex() - other.wallet.getAccountIndex();
    }
}
