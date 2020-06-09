package com.sparrowwallet.sparrow.event;

public class StatusEvent {
    private final String status;

    public StatusEvent(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
