package com.sparrowwallet.sparrow.event;

public class ConnectionFailedEvent {
    private final Throwable exception;

    public ConnectionFailedEvent(Throwable exception) {
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }
}
