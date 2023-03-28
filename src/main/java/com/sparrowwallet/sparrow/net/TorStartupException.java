package com.sparrowwallet.sparrow.net;

public class TorStartupException extends TorServerException {
    public TorStartupException(Throwable cause) {
        super(cause);
    }

    public TorStartupException(String message) {
        super(message);
    }

    public TorStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
