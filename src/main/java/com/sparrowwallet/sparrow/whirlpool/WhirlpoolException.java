package com.sparrowwallet.sparrow.whirlpool;

public class WhirlpoolException extends Exception {
    public WhirlpoolException(String message) {
        super(message);
    }

    public WhirlpoolException(String message, Throwable cause) {
        super(message, cause);
    }
}
