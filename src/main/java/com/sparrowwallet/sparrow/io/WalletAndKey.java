package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Map;

public class WalletAndKey implements Comparable<WalletAndKey> {
    private final Wallet wallet;
    private final ECKey encryptionKey;
    private final Key key;
    private final Map<WalletAndKey, Storage> childWallets;

    public WalletAndKey(Wallet wallet, ECKey encryptionKey, AsymmetricKeyDeriver keyDeriver, Map<WalletAndKey, Storage> childWallets) {
        this.wallet = wallet;
        this.encryptionKey = encryptionKey;
        this.key = encryptionKey == null ? null : new Key(encryptionKey.getPrivKeyBytes(), keyDeriver.getSalt(), EncryptionType.Deriver.ARGON2);
        this.childWallets = childWallets;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public ECKey getEncryptionKey() {
        return encryptionKey;
    }

    public Key getKey() {
        return key;
    }

    public Map<WalletAndKey, Storage> getChildWallets() {
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
    public int compareTo(WalletAndKey other) {
        return wallet.compareTo(other.wallet);
    }
}
