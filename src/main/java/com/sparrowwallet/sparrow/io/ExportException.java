package com.sparrowwallet.sparrow.io;

public class ExportException extends Exception {
    public ExportException() {
        super();
    }

    public ExportException(String message) {
        super(message);
    }

    public ExportException(Throwable cause) {
        super(cause);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
