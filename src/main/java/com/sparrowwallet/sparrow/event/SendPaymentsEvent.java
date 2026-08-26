package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class SendPaymentsEvent {
    private final Wallet wallet;
    private final List<Payment> payments;
    private final BitcoinURI bitcoinURI;

    public SendPaymentsEvent(Wallet wallet, List<Payment> payments) {
        this(wallet, payments, null);
    }

    public SendPaymentsEvent(Wallet wallet, List<Payment> payments, BitcoinURI bitcoinURI) {
        this.wallet = wallet;
        this.payments = payments;
        this.bitcoinURI = bitcoinURI;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public BitcoinURI getBitcoinURI() {
        return bitcoinURI;
    }
}
