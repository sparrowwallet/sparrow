package com.sparrowwallet.sparrow.io.bbqr;

public class BBQRException extends RuntimeException {
    public BBQRException(String message) {
        super(message);
    }

    public BBQRException(String message, Throwable cause) {
        super(message, cause);
    }
}
