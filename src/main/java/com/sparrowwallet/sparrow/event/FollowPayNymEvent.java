package com.sparrowwallet.sparrow.event;

import com.samourai.wallet.bip47.rpc.PaymentCode;

public class FollowPayNymEvent {
    private final String walletId;
    private final PaymentCode paymentCode;

    public FollowPayNymEvent(String walletId, PaymentCode paymentCode) {
        this.walletId = walletId;
        this.paymentCode = paymentCode;
    }

    public String getWalletId() {
        return walletId;
    }

    public PaymentCode getPaymentCode() {
        return paymentCode;
    }
}
