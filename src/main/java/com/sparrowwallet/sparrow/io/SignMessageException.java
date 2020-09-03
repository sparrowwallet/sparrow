package com.sparrowwallet.sparrow.io;

public class SignMessageException extends Exception {
    public SignMessageException() {
        super();
    }

    public SignMessageException(String message) {
        super(message);
    }

    public SignMessageException(Throwable cause) {
        super(cause);
    }

    public SignMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
