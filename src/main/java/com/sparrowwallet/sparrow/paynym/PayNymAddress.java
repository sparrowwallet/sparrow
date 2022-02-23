package com.sparrowwallet.sparrow.paynym;

import com.sparrowwallet.drongo.address.P2WPKHAddress;

public final class PayNymAddress extends P2WPKHAddress {
    private final PayNym payNym;

    public PayNymAddress(PayNym payNym) {
        super(new byte[20]);
        this.payNym = payNym;
    }

    public PayNym getPayNym() {
        return payNym;
    }

    public String toString() {
        return payNym.nymName();
    }
}
