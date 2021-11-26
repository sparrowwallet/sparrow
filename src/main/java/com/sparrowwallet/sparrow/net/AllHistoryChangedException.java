package com.sparrowwallet.sparrow.net;

public class AllHistoryChangedException extends RuntimeException {
    public AllHistoryChangedException() {
    }

    public AllHistoryChangedException(String message) {
        super(message);
    }

    public AllHistoryChangedException(String message, Throwable cause) {
        super(message, cause);
    }

    public AllHistoryChangedException(Throwable cause) {
        super(cause);
    }
}
