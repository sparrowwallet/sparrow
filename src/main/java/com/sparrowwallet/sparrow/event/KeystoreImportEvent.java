package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Keystore;

public class KeystoreImportEvent {
    private Keystore keystore;

    public KeystoreImportEvent(Keystore keystore) {
        this.keystore = keystore;
    }

    public Keystore getKeystore() {
        return keystore;
    }
}
