package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.time.Duration;
import java.util.Set;

public class CormorantScanStatusEvent extends CormorantStatusEvent {
    private final Set<Wallet> scanningWallets;
    private final int progress;
    private final Duration remainingDuration;

    public CormorantScanStatusEvent(String status, Set<Wallet> scanningWallets, int progress, Duration remainingDuration) {
        super(status);
        this.scanningWallets = scanningWallets;
        this.progress = progress;
        this.remainingDuration = remainingDuration;
    }

    @Override
    public boolean isFor(Wallet wallet) {
        return scanningWallets.contains(wallet);
    }

    public int getProgress() {
        return progress;
    }

    public boolean isCompleted() {
        return progress == 100;
    }

    public Duration getRemaining() {
        return remainingDuration;
    }

    public String getRemainingAsString() {
        if(remainingDuration != null) {
            if(progress < 30) {
                return Math.round((double)remainingDuration.toSeconds() / 60) + "m";
            } else {
                return remainingDuration.toMinutesPart() + "m " + remainingDuration.toSecondsPart() + "s";
            }
        }

        return "";
    }
}
