package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

public class BitcoinRPCException extends RuntimeException {
    public BitcoinRPCException(String message) {
        super(message);
    }
}
