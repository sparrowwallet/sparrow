package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public abstract class CormorantStatusEvent {
    private final String status;

    public CormorantStatusEvent(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public abstract boolean isFor(Wallet wallet);
}
