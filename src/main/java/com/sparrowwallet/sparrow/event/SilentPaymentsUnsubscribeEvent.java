package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.silentpayments.SilentPaymentScanAddress;

/**
 * Posted by ElectrumServer.releaseSilentPaymentSubscription when the refcount on a silent-payments
 * subscription reaches zero. The subscriber starts a background SilentPaymentsUnsubscribeService to
 * issue the actual unsubscribe RPC, since releaseSilentPaymentSubscription is reached from JFX-thread
 * paths (wallet close, refresh, history clear) where blocking on a network call is not acceptable.
 */
public class SilentPaymentsUnsubscribeEvent {
    private final SilentPaymentScanAddress scanAddress;

    public SilentPaymentsUnsubscribeEvent(SilentPaymentScanAddress scanAddress) {
        this.scanAddress = scanAddress;
    }

    public SilentPaymentScanAddress getScanAddress() {
        return scanAddress;
    }
}
