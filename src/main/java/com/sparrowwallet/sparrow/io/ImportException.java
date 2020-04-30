package com.sparrowwallet.sparrow.io;

public class ImportException extends Exception {
    public ImportException() {
        super();
    }

    public ImportException(String message) {
        super(message);
    }

    public ImportException(Throwable cause) {
        super(cause);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
