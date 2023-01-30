package com.sparrowwallet.sparrow.io;

import javax.smartcardio.CardException;

public class CardSignFailedException extends CardException {
    public CardSignFailedException(String message) {
        super(message);
    }

    public CardSignFailedException(Throwable cause) {
        super(cause);
    }

    public CardSignFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
