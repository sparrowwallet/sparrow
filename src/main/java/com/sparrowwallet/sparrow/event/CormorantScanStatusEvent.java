package com.sparrowwallet.sparrow.event;

import java.time.Duration;

public class CormorantScanStatusEvent extends CormorantStatusEvent {
    private final int progress;
    private final Duration remainingDuration;

    public CormorantScanStatusEvent(String status, int progress, Duration remainingDuration) {
        super(status);
        this.progress = progress;
        this.remainingDuration = remainingDuration;
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
