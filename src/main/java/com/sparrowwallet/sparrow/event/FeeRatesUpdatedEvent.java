package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.MempoolRateSize;

import java.util.Map;
import java.util.Set;

public class FeeRatesUpdatedEvent extends MempoolRateSizesUpdatedEvent {
    private final Map<Integer, Double> targetBlockFeeRates;
    private final Double nextBlockMedianFeeRate;

    public FeeRatesUpdatedEvent(Map<Integer, Double> targetBlockFeeRates, Set<MempoolRateSize> mempoolRateSizes) {
        this(targetBlockFeeRates, mempoolRateSizes, null);
    }

    public FeeRatesUpdatedEvent(Map<Integer, Double> targetBlockFeeRates, Set<MempoolRateSize> mempoolRateSizes, Double nextBlockMedianFeeRate) {
        super(mempoolRateSizes);
        this.targetBlockFeeRates = targetBlockFeeRates;
        this.nextBlockMedianFeeRate = nextBlockMedianFeeRate;
    }

    public Map<Integer, Double> getTargetBlockFeeRates() {
        return targetBlockFeeRates;
    }

    public Double getNextBlockMedianFeeRate() {
        return nextBlockMedianFeeRate;
    }
}
