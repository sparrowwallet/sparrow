package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.bip47.PaymentCode;
import javafx.scene.image.Image;

public class PayNymImageLoadedEvent {
    private final PaymentCode paymentCode;
    private final Image image;

    public PayNymImageLoadedEvent(PaymentCode paymentCode, Image image) {
        this.paymentCode = paymentCode;
        this.image = image;
    }

    public PaymentCode getPaymentCode() {
        return paymentCode;
    }

    public Image getImage() {
        return image;
    }
}
