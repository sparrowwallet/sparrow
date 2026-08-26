package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * Requests that the history of the given wallet be fetched again, for the places that can offer a refresh without holding the wallet's form.
 */
public class RequestWalletRefreshEvent {
    private final Wallet wallet;

    public RequestWalletRefreshEvent(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
