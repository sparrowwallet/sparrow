package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.MempoolRateSize;

import java.util.Map;
import java.util.Set;

public class FeeRatesUpdatedEvent extends MempoolRateSizesUpdatedEvent {
    private final Map<Integer, Double> targetBlockFeeRates;

    public FeeRatesUpdatedEvent(Map<Integer, Double> targetBlockFeeRates, Set<MempoolRateSize> mempoolRateSizes) {
        super(mempoolRateSizes);
        this.targetBlockFeeRates = targetBlockFeeRates;
    }

    public Map<Integer, Double> getTargetBlockFeeRates() {
        return targetBlockFeeRates;
    }
}
