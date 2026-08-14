package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.BlockSummary;

import java.util.Map;

public class BlockSummaryEvent {
    private final Map<Integer, BlockSummary> blockSummaryMap;
    private final Double nextBlockMedianFeeRate;

    public BlockSummaryEvent(Map<Integer, BlockSummary> blockSummaryMap, Double nextBlockMedianFeeRate) {
        this.blockSummaryMap = blockSummaryMap;
        this.nextBlockMedianFeeRate = nextBlockMedianFeeRate;
    }

    public Map<Integer, BlockSummary> getBlockSummaryMap() {
        return blockSummaryMap;
    }

    public Double getNextBlockMedianFeeRate() {
        return nextBlockMedianFeeRate;
    }
}
