package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Keystore;

/**
 * This event is used to indicate the animation for signing a keystore is complete
 */
public class KeystoreSignedEvent {
    private final Keystore keystore;

    public KeystoreSignedEvent(Keystore keystore) {
        this.keystore = keystore;
    }

    public Keystore getKeystore() {
        return keystore;
    }
}
