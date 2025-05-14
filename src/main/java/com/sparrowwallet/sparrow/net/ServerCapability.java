package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.AppServices;

public class ServerCapability {
    private final boolean supportsBatching;
    private final int maxTargetBlocks;
    private final boolean supportsRecentMempool;
    private final boolean supportsBlockStats;

    public ServerCapability(boolean supportsBatching) {
        this(supportsBatching, AppServices.TARGET_BLOCKS_RANGE.getLast());
    }

    public ServerCapability(boolean supportsBatching, int maxTargetBlocks) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = maxTargetBlocks;
        this.supportsRecentMempool = false;
        this.supportsBlockStats = false;
    }

    public ServerCapability(boolean supportsBatching, boolean supportsRecentMempool, boolean supportsBlockStats) {
        this(supportsBatching, AppServices.TARGET_BLOCKS_RANGE.getLast(), supportsRecentMempool, supportsBlockStats);
    }

    public ServerCapability(boolean supportsBatching, int maxTargetBlocks, boolean supportsRecentMempool, boolean supportsBlockStats) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = maxTargetBlocks;
        this.supportsRecentMempool = supportsRecentMempool;
        this.supportsBlockStats = supportsBlockStats;
    }

    public boolean supportsBatching() {
        return supportsBatching;
    }

    public int getMaxTargetBlocks() {
        return maxTargetBlocks;
    }

    public boolean supportsRecentMempool() {
        return supportsRecentMempool;
    }

    public boolean supportsBlockStats() {
        return supportsBlockStats;
    }
}
