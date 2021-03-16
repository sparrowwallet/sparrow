package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

public class WalletHistoryStartedEvent extends WalletHistoryStatusEvent {
    private final WalletNode walletNode;

    public WalletHistoryStartedEvent(Wallet wallet, WalletNode walletNode) {
        super(wallet, true);
        this.walletNode = walletNode;
    }

    public WalletNode getWalletNode() {
        return walletNode;
    }
}
