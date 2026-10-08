package com.sparrowwallet.sparrow.timelockrecovery;

public class TimelockRecoveryException extends Exception {
    public TimelockRecoveryException(String message) {
        super(message);
    }

    public TimelockRecoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
