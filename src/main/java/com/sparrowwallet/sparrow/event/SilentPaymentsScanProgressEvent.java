package com.sparrowwallet.sparrow.event;

/**
 * Posted by SubscriptionService on every blockchain.silentpayments.subscribe notification.
 * Carries the SP address and the scan's progress (0.0–1.0). WalletForm consumers use this for
 * status UI; the gating decision (whether to display) is per-wallet runtime state, not encoded here.
 */
public class SilentPaymentsScanProgressEvent {
    private final String spAddress;
    private final double progress;

    public SilentPaymentsScanProgressEvent(String spAddress, double progress) {
        this.spAddress = spAddress;
        this.progress = progress;
    }

    public String getSpAddress() {
        return spAddress;
    }

    public double getProgress() {
        return progress;
    }
}
