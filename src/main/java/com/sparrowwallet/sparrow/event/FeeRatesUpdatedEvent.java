package com.sparrowwallet.sparrow.event;

import java.util.Map;

public class FeeRatesUpdatedEvent {
    private final Map<Integer, Double> targetBlockFeeRates;
    private final Map<Long, Long> feeRateHistogram;

    public FeeRatesUpdatedEvent(Map<Integer, Double> targetBlockFeeRates, Map<Long, Long> feeRateHistogram) {
        this.targetBlockFeeRates = targetBlockFeeRates;
        this.feeRateHistogram = feeRateHistogram;
    }

    public Map<Integer, Double> getTargetBlockFeeRates() {
        return targetBlockFeeRates;
    }

    public Map<Long, Long> getFeeRateHistogram() {
        return feeRateHistogram;
    }
}
