package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.AppServices;

public class ServerCapability {
    private final boolean supportsBatching;
    private final int maxTargetBlocks;

    public ServerCapability(boolean supportsBatching) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = AppServices.TARGET_BLOCKS_RANGE.getLast();
    }

    public ServerCapability(boolean supportsBatching, int maxTargetBlocks) {
        this.supportsBatching = supportsBatching;
        this.maxTargetBlocks = maxTargetBlocks;
    }

    public boolean supportsBatching() {
        return supportsBatching;
    }

    public int getMaxTargetBlocks() {
        return maxTargetBlocks;
    }
}
