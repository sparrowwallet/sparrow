package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class KeystoreEncryptionChangedEvent extends WalletSettingsChangedEvent {
    private List<Keystore> changedKeystores;

    public KeystoreEncryptionChangedEvent(Wallet wallet, Wallet pastWallet, String walletId, List<Keystore> changedKeystores) {
        super(wallet, pastWallet, walletId);
        this.changedKeystores = changedKeystores;
    }

    public List<Keystore> getChangedKeystores() {
        return changedKeystores;
    }
}
