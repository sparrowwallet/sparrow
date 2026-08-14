package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Payment;

import java.util.List;

public class RequestSendToManyEvent {
    private final List<Payment> payments;

    public RequestSendToManyEvent(List<Payment> payments) {
        this.payments = payments;
    }

    public List<Payment> getPayments() {
        return payments;
    }
}
