package com.sparrowwallet.sparrow.wallet;

public enum Function {
    TRANSACTIONS("transactions"), SEND("send"), RECEIVE("receive"), ADDRESSES("addresses"), UTXOS("utxos"), SETTINGS("settings"), LOCK("lock");

    private final String name;

    Function(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
