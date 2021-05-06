package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class SendPaymentsEvent {
    private final Wallet wallet;
    private final List<Payment> payments;

    public SendPaymentsEvent(Wallet wallet, List<Payment> payments) {
        this.wallet = wallet;
        this.payments = payments;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<Payment> getPayments() {
        return payments;
    }
}
