package com.sparrowwallet.sparrow.net;

public enum ServerType {
    BITCOIN_CORE("Bitcoin Core"), ELECTRUM_SERVER("Private Electrum"), PUBLIC_ELECTRUM_SERVER("Public Electrum"), IROH_SERVER("Iroh P2P");

    private final String name;

    ServerType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
