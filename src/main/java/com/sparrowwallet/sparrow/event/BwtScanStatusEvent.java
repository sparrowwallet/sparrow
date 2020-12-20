package com.sparrowwallet.sparrow.event;

import java.util.Date;

public class BwtScanStatusEvent extends BwtStatusEvent {
    private final int progress;
    private final Date eta;

    public BwtScanStatusEvent(String status, int progress, Date eta) {
        super(status);
        this.progress = progress;
        this.eta = eta;
    }

    public int getProgress() {
        return progress;
    }

    public Date getEta() {
        return eta;
    }
}
