package com.sparrowwallet.sparrow.event;

public class BwtSyncStatusEvent extends BwtStatusEvent {
    private final int progress;
    private final int tip;

    public BwtSyncStatusEvent(String status, int progress, int tip) {
        super(status);
        this.progress = progress;
        this.tip = tip;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isCompleted() {
        return progress == 100;
    }

    public int getTip() {
        return tip;
    }
}
