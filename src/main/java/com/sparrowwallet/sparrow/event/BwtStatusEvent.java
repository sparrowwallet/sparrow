package com.sparrowwallet.sparrow.event;

public class BwtStatusEvent {
    private final String status;

    public BwtStatusEvent(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
