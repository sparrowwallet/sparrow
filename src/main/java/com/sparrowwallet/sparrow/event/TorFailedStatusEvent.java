package com.sparrowwallet.sparrow.event;

public class TorFailedStatusEvent extends TorStatusEvent {
    private final Throwable exception;

    public TorFailedStatusEvent(Throwable exception) {
        super("Tor failed to start: " + (exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage()));
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }
}
