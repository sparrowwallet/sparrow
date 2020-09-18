package com.sparrowwallet.sparrow.event;

public class WalletHistoryStatusEvent {
    private final boolean loaded;
    private final String statusMessage;
    private final String errorMessage;

    public WalletHistoryStatusEvent(boolean loaded) {
        this.loaded = loaded;
        this.statusMessage = null;
        this.errorMessage = null;
    }

    public WalletHistoryStatusEvent(boolean loaded, String statusMessage) {
        this.loaded = false;
        this.statusMessage = statusMessage;
        this.errorMessage = null;
    }

    public WalletHistoryStatusEvent(String errorMessage) {
        this.loaded = false;
        this.statusMessage = null;
        this.errorMessage = errorMessage;
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
