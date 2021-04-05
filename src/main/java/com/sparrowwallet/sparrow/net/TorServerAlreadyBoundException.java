package com.sparrowwallet.sparrow.net;

public class TorServerAlreadyBoundException extends TorServerException {
    public TorServerAlreadyBoundException(Throwable cause) {
        super(cause);
    }

    public TorServerAlreadyBoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
