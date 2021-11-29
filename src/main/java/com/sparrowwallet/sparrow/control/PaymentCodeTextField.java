package com.sparrowwallet.sparrow.control;

import com.samourai.wallet.bip47.rpc.PaymentCode;

public class PaymentCodeTextField extends CopyableTextField {
    private String paymentCodeStr;

    public void setPaymentCode(PaymentCode paymentCode) {
        this.paymentCodeStr = paymentCode.toString();
        String abbrevPcode = paymentCodeStr.substring(0, 12) + "..." + paymentCodeStr.substring(paymentCodeStr.length() - 5);
        setText(abbrevPcode);
    }

    @Override
    protected String getCopyText() {
        return paymentCodeStr;
    }
}
