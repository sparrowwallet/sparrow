package com.sparrowwallet.sparrow.event;

public class TorStatusEvent {
    private final String status;

    public TorStatusEvent(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
