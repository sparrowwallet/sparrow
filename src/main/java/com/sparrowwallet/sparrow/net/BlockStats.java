package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sparrowwallet.sparrow.BlockSummary;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlockStats(int height, String blockhash, double[] feerate_percentiles, int total_weight, int txs, long time) {
    public BlockSummary toBlockSummary() {
        Double medianFee = feerate_percentiles != null && feerate_percentiles.length > 0 ? feerate_percentiles[feerate_percentiles.length / 2] : null;
        return new BlockSummary(height, new Date(time * 1000), medianFee, txs, total_weight);
    }
}
