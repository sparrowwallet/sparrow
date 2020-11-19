package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.MempoolRateSize;

import java.util.Map;
import java.util.Set;

public class FeeRatesUpdatedEvent {
    private final Map<Integer, Double> targetBlockFeeRates;
    private final Set<MempoolRateSize> mempoolRateSizes;

    public FeeRatesUpdatedEvent(Map<Integer, Double> targetBlockFeeRates, Set<MempoolRateSize> mempoolRateSizes) {
        this.targetBlockFeeRates = targetBlockFeeRates;
        this.mempoolRateSizes = mempoolRateSizes;
    }

    public Map<Integer, Double> getTargetBlockFeeRates() {
        return targetBlockFeeRates;
    }

    public Set<MempoolRateSize> getMempoolRateSizes() {
        return mempoolRateSizes;
    }
}
