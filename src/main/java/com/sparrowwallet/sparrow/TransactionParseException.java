package com.sparrowwallet.sparrow;

public class TransactionParseException extends Exception {
    public TransactionParseException() {
        super();
    }

    public TransactionParseException(String message) {
        super(message);
    }

    public TransactionParseException(Throwable cause) {
        super(cause);
    }

    public TransactionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

