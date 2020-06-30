package com.sparrowwallet.sparrow.event;

import java.util.Map;

public class FeeRatesUpdatedEvent {
    private final Map<Integer, Double> targetBlockFeeRates;

    public FeeRatesUpdatedEvent(Map<Integer, Double> targetBlockFeeRates) {
        this.targetBlockFeeRates = targetBlockFeeRates;
    }

    public Map<Integer, Double> getTargetBlockFeeRates() {
        return targetBlockFeeRates;
    }
}
