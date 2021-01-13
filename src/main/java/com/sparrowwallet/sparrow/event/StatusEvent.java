package com.sparrowwallet.sparrow.event;

public class StatusEvent {
    public static final int DEFAULT_SHOW_DURATION_SECS = 20;

    private final String status;
    private final int showDuration;

    public StatusEvent(String status) {
        this(status, DEFAULT_SHOW_DURATION_SECS);
    }

    public StatusEvent(String status, int showDuration) {
        this.status = status;
        this.showDuration = showDuration;
    }

    public String getStatus() {
        return status;
    }

    public int getShowDuration() {
        return showDuration;
    }
}
