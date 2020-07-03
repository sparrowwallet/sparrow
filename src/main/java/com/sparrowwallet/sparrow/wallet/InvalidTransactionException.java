package com.sparrowwallet.sparrow.wallet;

public class InvalidTransactionException extends Exception {
    public InvalidTransactionException() {
        super();
    }

    public InvalidTransactionException(String msg) {
        super(msg);
    }

    /**
     * Thrown when there are not enough selected inputs to pay the total output value
     */
    public static class InsufficientInputsException extends InvalidTransactionException {
        public InsufficientInputsException(String msg) {
            super(msg);
        }
    }
}
