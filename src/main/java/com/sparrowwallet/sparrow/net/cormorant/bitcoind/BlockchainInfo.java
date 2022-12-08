package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlockchainInfo(int blocks, int headers, String bestblockhash, boolean initialblockdownload, long time, Double verificationprogress, boolean pruned, Integer pruneheight) {
    public Date getTip() {
        return new Date(time * 1000);
    }

    public int getProgressPercent() {
        return (int) Math.round(verificationprogress * 100);
    }
}
