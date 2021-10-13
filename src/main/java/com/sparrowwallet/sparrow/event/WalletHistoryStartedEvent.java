package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.Set;

public class WalletHistoryStartedEvent extends WalletHistoryStatusEvent {
    private final Set<WalletNode> walletNodes;

    public WalletHistoryStartedEvent(Wallet wallet, Set<WalletNode> walletNodes) {
        super(wallet, true);
        this.walletNodes = walletNodes;
    }

    public Set<WalletNode> getWalletNodes() {
        return walletNodes;
    }
}
