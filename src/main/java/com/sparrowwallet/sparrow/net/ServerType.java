package com.sparrowwallet.sparrow.net;

public enum ServerType {
    BITCOIN_CORE("Bitcoin Core"), ELECTRUM_SERVER("Electrum Server");

    private final String name;

    ServerType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
