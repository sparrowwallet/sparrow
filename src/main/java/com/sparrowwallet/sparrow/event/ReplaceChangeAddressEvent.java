package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.WalletTransaction;

public class ReplaceChangeAddressEvent {
    private final WalletTransaction walletTransaction;

    public ReplaceChangeAddressEvent(WalletTransaction walletTx) {
        this.walletTransaction = walletTx;
    }

    public WalletTransaction getWalletTransaction() {
        return walletTransaction;
    }
}
