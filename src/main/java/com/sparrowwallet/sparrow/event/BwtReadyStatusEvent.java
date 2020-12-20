package com.sparrowwallet.sparrow.event;

public class BwtReadyStatusEvent extends BwtStatusEvent {
    private final long shutdownPtr;

    public BwtReadyStatusEvent(String status, long shutdownPtr) {
        super(status);
        this.shutdownPtr = shutdownPtr;
    }

    public long getShutdownPtr() {
        return shutdownPtr;
    }
}
