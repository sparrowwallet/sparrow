package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletHistoryStatusEvent {
    private final Wallet wallet;
    private final boolean loaded;
    private final String statusMessage;
    private final String errorMessage;

    public WalletHistoryStatusEvent(Wallet wallet, boolean loaded) {
        this.wallet = wallet;
        this.loaded = loaded;
        this.statusMessage = null;
        this.errorMessage = null;
    }

    public WalletHistoryStatusEvent(Wallet wallet, boolean loaded, String statusMessage) {
        this.wallet = wallet;
        this.loaded = false;
        this.statusMessage = statusMessage;
        this.errorMessage = null;
    }

    public WalletHistoryStatusEvent(Wallet wallet,String errorMessage) {
        this.wallet = wallet;
        this.loaded = false;
        this.statusMessage = null;
        this.errorMessage = errorMessage;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public boolean isLoading() {
        return !loaded;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
