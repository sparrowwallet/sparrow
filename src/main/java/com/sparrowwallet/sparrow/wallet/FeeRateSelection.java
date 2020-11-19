package com.sparrowwallet.sparrow.wallet;

public enum FeeRateSelection {
    BLOCK_TARGET("Block Target"), MEMPOOL_SIZE("Mempool Size");

    private final String name;

    private FeeRateSelection(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
