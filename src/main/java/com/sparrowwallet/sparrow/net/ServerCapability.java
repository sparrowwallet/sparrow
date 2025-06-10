package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.AppServices;

public class ServerCapability {
    private final boolean supportsBatching;
    private final int maxTargetBlocks;
    private final boolean supportsRecentMempool;
    private final boolean supportsBlockStats;
    private final boolean supportsUnsubscribe;

    public ServerCapability(boolean supportsBatching, boolean supportsUnsubscribe) {
        this(supportsBatching, AppServices.TARGET_BLOCKS_RANGE.getLast(), supportsUnsubscribe);
    }

    public ServerCapability(boolean supportsBatching, int maxTargetBlocks, boolean supportsUnsubscribe) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = maxTargetBlocks;
        this.supportsRecentMempool = false;
        this.supportsBlockStats = false;
        this.supportsUnsubscribe = supportsUnsubscribe;
    }

    public ServerCapability(boolean supportsBatching, boolean supportsRecentMempool, boolean supportsBlockStats, boolean supportsUnsubscribe) {
        this(supportsBatching, AppServices.TARGET_BLOCKS_RANGE.getLast(), supportsRecentMempool, supportsBlockStats, supportsUnsubscribe);
    }

    public ServerCapability(boolean supportsBatching, int maxTargetBlocks, boolean supportsRecentMempool, boolean supportsBlockStats, boolean supportsUnsubscribe) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = maxTargetBlocks;
        this.supportsRecentMempool = supportsRecentMempool;
        this.supportsBlockStats = supportsBlockStats;
        this.supportsUnsubscribe = supportsUnsubscribe;
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

    public boolean supportsUnsubscribe() {
        return supportsUnsubscribe;
    }
}
