package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CormorantSyncStatusEvent extends CormorantStatusEvent {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    private final int progress;
    private final Date tip;

    public CormorantSyncStatusEvent(String status, int progress, Date tip) {
        super(status);
        this.progress = progress;
        this.tip = tip;
    }

    @Override
    public boolean isFor(Wallet wallet) {
        return true;
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
