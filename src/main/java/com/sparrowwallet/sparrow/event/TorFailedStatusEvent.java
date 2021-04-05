package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.TorServerAlreadyBoundException;

public class TorFailedStatusEvent extends TorStatusEvent {
    private final Throwable exception;

    public TorFailedStatusEvent(Throwable exception) {
        super("Tor failed to start: " + (exception instanceof TorServerAlreadyBoundException ? exception.getCause().getMessage() + " Is a Tor proxy already running?" :
                (exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage())));
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }
}
