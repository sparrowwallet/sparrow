package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.MempoolRateSize;

import java.util.Set;

public class MempoolRateSizesUpdatedEvent {
    private final Set<MempoolRateSize> mempoolRateSizes;

    public MempoolRateSizesUpdatedEvent(Set<MempoolRateSize> mempoolRateSizes) {
        this.mempoolRateSizes = mempoolRateSizes;
    }

    public Set<MempoolRateSize> getMempoolRateSizes() {
        return mempoolRateSizes;
    }
}
