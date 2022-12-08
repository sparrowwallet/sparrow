package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletScanningInfo {
    private final boolean scanning;
    private final int duration;
    private final double progress;

    public WalletScanningInfo(boolean scanning) {
        this.scanning = scanning;
        this.duration = 0;
        this.progress = 0;
    }

    public WalletScanningInfo(Integer duration,Double progress) {
        this.scanning = true;
        this.duration = duration;
        this.progress = progress;
    }

    public boolean isScanning() {
        return scanning;
    }

    public int getDuration() {
        return duration;
    }

    public double getProgress() {
        return progress;
    }

    public int getPercent() {
        return (int) (progress * 100.0);
    }

    public Duration getRemaining() {
        long total = Math.round(duration / progress);
        return Duration.ofSeconds(total - duration);
    }

    @JsonCreator
    private static WalletScanningInfo fromJson(boolean scanning) {
        return new WalletScanningInfo(scanning);
    }

    @JsonCreator
    public static WalletScanningInfo fromJson(@JsonProperty("duration") Integer duration, @JsonProperty("progress") Double progress) {
        return new WalletScanningInfo(duration, progress);
    }
}
