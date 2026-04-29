package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.SilentPaymentsSubscription;
import com.sparrowwallet.sparrow.net.SilentPaymentsTx;

import java.util.List;

/**
 * Posted when a blockchain.silentpayments.subscribe notification is received from an Electrum server.
 * Carries the subscription payload, the progress (with 1.0 marking the historical-scan-complete
 * moment per BIP352), and the list of silent-payment transactions in this batch.
 * <p>
 * The event holds no wallet reference; consumers (typically a WalletForm) match
 * {@link SilentPaymentsSubscription#address} against their wallet's silent-payment address and
 * ignore the event otherwise. This mirrors how WalletNodeHistoryChangedEvent is consumed and avoids
 * pinning closed wallets through static event-router state.
 */
public class SilentPaymentsNotificationEvent {
    private final SilentPaymentsSubscription subscription;
    private final double progress;
    private final List<SilentPaymentsTx> history;

    public SilentPaymentsNotificationEvent(SilentPaymentsSubscription subscription, double progress, List<SilentPaymentsTx> history) {
        this.subscription = subscription;
        this.progress = progress;
        this.history = history;
    }

    public SilentPaymentsSubscription getSubscription() {
        return subscription;
    }

    public double getProgress() {
        return progress;
    }

    public List<SilentPaymentsTx> getHistory() {
        return history;
    }
}
