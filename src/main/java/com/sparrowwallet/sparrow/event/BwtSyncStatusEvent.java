package com.sparrowwallet.sparrow.event;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BwtSyncStatusEvent extends BwtStatusEvent {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm");

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

    public String getTipAsString() {
        return tip == null ? "" : DATE_FORMAT.format(tip);
    }
}
