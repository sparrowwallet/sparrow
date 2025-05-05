package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.BlockSummary;

import java.util.Map;

public class BlockSummaryEvent {
    private final Map<Integer, BlockSummary> blockSummaryMap;

    public BlockSummaryEvent(Map<Integer, BlockSummary> blockSummaryMap) {
        this.blockSummaryMap = blockSummaryMap;
    }

    public Map<Integer, BlockSummary> getBlockSummaryMap() {
        return blockSummaryMap;
    }
}
