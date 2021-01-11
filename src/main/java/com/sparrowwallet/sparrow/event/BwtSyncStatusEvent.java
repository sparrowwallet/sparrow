package com.sparrowwallet.sparrow.event;

import java.util.Date;

public class BwtSyncStatusEvent extends BwtStatusEvent {
    private final int progress;
    private final Date tip;

    public BwtSyncStatusEvent(String status, int progress, Date tip) {
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

    public Date getTip() {
        return tip;
    }
}
