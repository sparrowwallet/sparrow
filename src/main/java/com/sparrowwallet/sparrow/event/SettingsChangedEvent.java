package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class SettingsChangedEvent {
    private final Wallet wallet;
    private final Type type;

    public SettingsChangedEvent(Wallet wallet, Type type) {
        this.wallet = wallet;
        this.type = type;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        POLICY, SCRIPT_TYPE, MUTLISIG_THRESHOLD, MULTISIG_TOTAL, KEYSTORE_LABEL, KEYSTORE_FINGERPRINT, KEYSTORE_DERIVATION, KEYSTORE_XPUB, GAP_LIMIT, BIRTH_DATE, WATCH_LAST;
    }
}
