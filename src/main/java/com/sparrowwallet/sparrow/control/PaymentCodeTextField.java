package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.bip47.PaymentCode;

public class PaymentCodeTextField extends CopyableTextField {
    private String paymentCodeStr;

    public void setPaymentCode(PaymentCode paymentCode) {
        this.paymentCodeStr = paymentCode.toString();
        setPaymentCodeString();
    }

    public void setPaymentCode(com.samourai.wallet.bip47.rpc.PaymentCode paymentCode) {
        this.paymentCodeStr = paymentCode.toString();
        setPaymentCodeString();
    }

    private void setPaymentCodeString() {
        String abbrevPcode = paymentCodeStr.substring(0, 12) + "..." + paymentCodeStr.substring(paymentCodeStr.length() - 5);
        setText(abbrevPcode);
    }

    @Override
    protected String getCopyText() {
        return paymentCodeStr;
    }
}
