package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

/**
 * This event is trigger when one or more keystores on a wallet are updated, and the wallet is saved
 */
public class KeystoreLabelsChangedEvent extends WalletSettingsChangedEvent {
    private final List<Keystore> changedKeystores;

    public KeystoreLabelsChangedEvent(Wallet wallet, Wallet pastWallet, String walletId, List<Keystore> changedKeystores) {
        super(wallet, pastWallet, walletId);
        this.changedKeystores = changedKeystores;
    }

    public List<Keystore> getChangedKeystores() {
        return changedKeystores;
    }
}
