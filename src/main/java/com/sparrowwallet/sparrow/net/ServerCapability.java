package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.AppServices;

import java.util.Collections;
import java.util.List;

public class ServerCapability {
    private final boolean supportsBatching;
    private final int maxTargetBlocks;
    private final boolean supportsRecentMempool;
    private final boolean supportsBlockStats;
    private final boolean supportsUnsubscribe;
    private final boolean supportsServerFeatures;
    private List<Integer> supportedSilentPaymentsVersions = Collections.emptyList();

    public ServerCapability(boolean supportsBatching, boolean supportsUnsubscribe, boolean supportsServerFeatures) {
        this(supportsBatching, AppServices.TARGET_BLOCKS_RANGE.getLast(), supportsUnsubscribe, supportsServerFeatures);
    }

    public ServerCapability(boolean supportsBatching, int maxTargetBlocks, boolean supportsUnsubscribe, boolean supportsServerFeatures) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = maxTargetBlocks;
        this.supportsRecentMempool = false;
        this.supportsBlockStats = false;
        this.supportsUnsubscribe = supportsUnsubscribe;
        this.supportsServerFeatures = supportsServerFeatures;
    }

    public ServerCapability(boolean supportsBatching, boolean supportsRecentMempool, boolean supportsBlockStats, boolean supportsUnsubscribe, boolean supportsServerFeatures) {
        this(supportsBatching, AppServices.TARGET_BLOCKS_RANGE.getLast(), supportsRecentMempool, supportsBlockStats, supportsUnsubscribe, supportsServerFeatures);
    }

    public ServerCapability(boolean supportsBatching, int maxTargetBlocks, boolean supportsRecentMempool, boolean supportsBlockStats, boolean supportsUnsubscribe, boolean supportsServerFeatures) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = maxTargetBlocks;
        this.supportsRecentMempool = supportsRecentMempool;
        this.supportsBlockStats = supportsBlockStats;
        this.supportsUnsubscribe = supportsUnsubscribe;
        this.supportsServerFeatures = supportsServerFeatures;
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

    public boolean supportsServerFeatures() {
        return supportsServerFeatures;
    }

    public List<Integer> getSupportedSilentPaymentsVersions() {
        return supportedSilentPaymentsVersions;
    }

    public boolean supportsSilentPayments() {
        return supportedSilentPaymentsVersions.contains(0);
    }

    public ServerCapability withServerFeatures(ServerFeatures features) {
        if(features != null && features.silent_payments != null) {
            this.supportedSilentPaymentsVersions = List.copyOf(features.silent_payments);
        }
        return this;
    }
}
