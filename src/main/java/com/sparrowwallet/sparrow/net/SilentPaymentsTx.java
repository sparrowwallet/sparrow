package com.sparrowwallet.sparrow.net;

public class SilentPaymentsTx {
    public int height;
    public String tx_hash;
    public String tweak_key;

    public SilentPaymentsTx() {}

    public SilentPaymentsTx(int height, String tx_hash, String tweak_key) {
        this.height = height;
        this.tx_hash = tx_hash;
        this.tweak_key = tweak_key;
    }

    @Override
    public String toString() {
        return "SilentPaymentsTx{height=" + height + ", tx_hash='" + tx_hash + "', tweak_key='" + tweak_key + "'}";
    }
}
