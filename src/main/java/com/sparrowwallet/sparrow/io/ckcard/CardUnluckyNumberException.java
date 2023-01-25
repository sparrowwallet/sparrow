package com.sparrowwallet.sparrow.io.ckcard;

import javax.smartcardio.CardException;

public class CardUnluckyNumberException extends CardException {
    public CardUnluckyNumberException(String message) {
        super(message);
    }

    public CardUnluckyNumberException(Throwable cause) {
        super(cause);
    }

    public CardUnluckyNumberException(String message, Throwable cause) {
        super(message, cause);
    }
}
