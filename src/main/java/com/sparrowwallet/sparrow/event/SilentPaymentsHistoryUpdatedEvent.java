package com.sparrowwallet.sparrow.event;

/**
 * Posted by SubscriptionService on a live silent-payments delta — i.e. a progress = 1.0 notification
 * that arrives after the historical scan has already completed. Carries the SP address only;
 * consumers re-call ElectrumServer.getSilentPaymentHistory(scanAddress, neededStart) to get the
 * full cache and apply their own wallet.getWalletTransaction(txid) filter for "what's new for me".
 * <p>
 * The first historical-scan-complete notification is consumed by the blocking getSilentPaymentHistory
 * call's latch and does NOT post this event.
 */
public class SilentPaymentsHistoryUpdatedEvent {
    private final String spAddress;

    public SilentPaymentsHistoryUpdatedEvent(String spAddress) {
        this.spAddress = spAddress;
    }

    public String getSpAddress() {
        return spAddress;
    }
}
