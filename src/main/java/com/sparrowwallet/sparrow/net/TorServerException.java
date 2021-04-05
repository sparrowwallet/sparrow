package com.sparrowwallet.sparrow.net;

public class TorServerException extends ServerException {
    public TorServerException(Throwable cause) {
        super(cause);
    }

    public TorServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
