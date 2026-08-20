package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.Set;

public class CormorantImportStatusEvent extends CormorantStatusEvent {
    private final Set<Wallet> wallets;
    private final String errorMessage;

    public CormorantImportStatusEvent(String status, Set<Wallet> wallets, String errorMessage) {
        super(status);
        this.wallets = wallets;
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean isFor(Wallet wallet) {
        return wallets.contains(wallet);
    }

    public Set<Wallet> getWallets() {
        return wallets;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
