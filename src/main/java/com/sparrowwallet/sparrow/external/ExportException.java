package com.sparrowwallet.sparrow.external;

public class ExportException extends Throwable {
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
