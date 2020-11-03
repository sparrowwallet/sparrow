package com.sparrowwallet.sparrow.payjoin;

public class PayjoinReceiverException extends Exception {
    public PayjoinReceiverException() {
        super();
    }

    public PayjoinReceiverException(String msg) {
        super(msg);
    }

    public PayjoinReceiverException(Throwable cause) {
        super(cause);
    }

    public PayjoinReceiverException(String message, Throwable cause) {
        super(message, cause);
    }
}
