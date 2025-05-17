package com.sparrowwallet.sparrow.wallet;

public enum FeeRatesSelection {
    BLOCK_TARGET("Block Target"), MEMPOOL_SIZE("Mempool Size"), RECENT_BLOCKS("Recent Blocks");

    private final String name;

    private FeeRatesSelection(String name) {
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
