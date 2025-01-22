package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class KeystoreDeviceRegistrationsChangedEvent extends WalletChangedEvent {
    private final List<Keystore> changedKeystores;

    public KeystoreDeviceRegistrationsChangedEvent(Wallet wallet, List<Keystore> changedKeystores) {
        super(wallet);
        this.changedKeystores = changedKeystores;
    }

    public List<Keystore> getChangedKeystores() {
        return changedKeystores;
    }
}
