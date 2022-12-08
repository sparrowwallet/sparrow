package com.sparrowwallet.sparrow.event;

public class CormorantStatusEvent {
    private final String status;

    public CormorantStatusEvent(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
