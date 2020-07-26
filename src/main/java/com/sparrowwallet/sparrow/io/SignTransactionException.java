package com.sparrowwallet.sparrow.io;

public class SignTransactionException extends Exception {
    public SignTransactionException() {
        super();
    }

    public SignTransactionException(String message) {
        super(message);
    }

    public SignTransactionException(Throwable cause) {
        super(cause);
    }

    public SignTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
