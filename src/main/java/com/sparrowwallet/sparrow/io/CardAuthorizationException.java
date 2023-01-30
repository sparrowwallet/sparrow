package com.sparrowwallet.sparrow.io;

import javax.smartcardio.CardException;

public class CardAuthorizationException extends CardException {
    public CardAuthorizationException(String message) {
        super(message);
    }

    public CardAuthorizationException(Throwable cause) {
        super(cause);
    }

    public CardAuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
