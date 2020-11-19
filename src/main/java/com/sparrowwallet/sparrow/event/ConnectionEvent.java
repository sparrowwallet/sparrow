package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.protocol.BlockHeader;
import com.sparrowwallet.sparrow.net.MempoolRateSize;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConnectionEvent extends FeeRatesUpdatedEvent {
    private final List<String> serverVersion;
    private final String serverBanner;
    private final int blockHeight;
    private final BlockHeader blockHeader;
    private final Double minimumRelayFeeRate;

    public ConnectionEvent(List<String> serverVersion, String serverBanner, int blockHeight, BlockHeader blockHeader, Map<Integer, Double> targetBlockFeeRates, Set<MempoolRateSize> mempoolRateSizes, Double minimumRelayFeeRate) {
        super(targetBlockFeeRates, mempoolRateSizes);
        this.serverVersion = serverVersion;
        this.serverBanner = serverBanner;
        this.blockHeight = blockHeight;
        this.blockHeader = blockHeader;
        this.minimumRelayFeeRate = minimumRelayFeeRate;
    }

    public List<String> getServerVersion() {
        return serverVersion;
    }

    public String getServerBanner() {
        return serverBanner;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public BlockHeader getBlockHeader() {
        return blockHeader;
    }

    public Double getMinimumRelayFeeRate() {
        return minimumRelayFeeRate;
    }
}
