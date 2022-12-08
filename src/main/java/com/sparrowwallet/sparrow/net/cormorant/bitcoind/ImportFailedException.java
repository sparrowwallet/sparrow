package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

public class ImportFailedException extends Exception {
    public ImportFailedException(String message) {
        super(message);
    }
}
