package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Keystore;

public class KeystoreExportEvent {
    private final Keystore keystore;

    public KeystoreExportEvent(Keystore keystore) {
        this.keystore = keystore;
    }

    public Keystore getKeystore() {
        return keystore;
    }
}
