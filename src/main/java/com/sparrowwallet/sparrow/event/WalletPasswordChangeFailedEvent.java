package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

/**
 * Posted from the wallet update thread when the wallet file could not be written with a new password, which the code that changed the password
 * has already returned from and so cannot report.
 */
public class WalletPasswordChangeFailedEvent {
    private final Wallet wallet;
    private final boolean passwordRemoved;
    private final Throwable exception;

    public WalletPasswordChangeFailedEvent(Wallet wallet, boolean passwordRemoved, Throwable exception) {
        this.wallet = wallet;
        this.passwordRemoved = passwordRemoved;
        this.exception = exception;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public boolean isPasswordRemoved() {
        return passwordRemoved;
    }

    public Throwable getException() {
        return exception;
    }
}
