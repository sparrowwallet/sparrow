package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * This event is used by the DeviceSignMessageDialog to indicate that a USB device has signed a message
 *
 */
public class MessageSignedEvent {
    private final Wallet wallet;
    private final String signature;

    public MessageSignedEvent(Wallet wallet, String signature) {
        this.wallet = wallet;
        this.signature = signature;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getSignature() {
        return signature;
    }
}
