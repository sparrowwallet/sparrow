package com.sparrowwallet.sparrow.io;

public class DisplayAddressException extends Exception {
    public DisplayAddressException() {
        super();
    }

    public DisplayAddressException(String message) {
        super(message);
    }

    public DisplayAddressException(Throwable cause) {
        super(cause);
    }

    public DisplayAddressException(String message, Throwable cause) {
        super(message, cause);
    }
}
