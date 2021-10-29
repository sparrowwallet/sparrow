package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.StandardAccount;

import java.util.Map;

public class KeystoresDiscoveredEvent {
    private final Map<StandardAccount, Keystore> discoveredKeystores;

    public KeystoresDiscoveredEvent(Map<StandardAccount, Keystore> discoveredKeystores) {
        this.discoveredKeystores = discoveredKeystores;
    }

    public Map<StandardAccount, Keystore> getDiscoveredKeystores() {
        return discoveredKeystores;
    }
}
