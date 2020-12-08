package com.sparrowwallet.sparrow;

public class TabData {
    private final TabType type;

    public TabData(TabType type) {
        this.type = type;
    }

    public TabType getType() {
        return type;
    }

    public enum TabType {
        WALLET, TRANSACTION
    }
}
