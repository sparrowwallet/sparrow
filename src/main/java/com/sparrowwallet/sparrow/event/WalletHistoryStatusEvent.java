package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletHistoryStatusEvent {
    private final Wallet wallet;
    private final boolean loading;
    private final String statusMessage;
    private final String errorMessage;

    public WalletHistoryStatusEvent(Wallet wallet, boolean loading) {
        this.wallet = wallet;
        this.loading = loading;
        this.statusMessage = null;
        this.errorMessage = null;
    }

    public WalletHistoryStatusEvent(Wallet wallet, boolean loading, String statusMessage) {
        this.wallet = wallet;
        this.loading = loading;
        this.statusMessage = statusMessage;
        this.errorMessage = null;
    }

    public WalletHistoryStatusEvent(Wallet wallet, String errorMessage) {
        this.wallet = wallet;
        this.loading = false;
        this.statusMessage = null;
        this.errorMessage = errorMessage;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
