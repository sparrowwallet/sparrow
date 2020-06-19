package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.List;

public class WalletHistoryChangedEvent extends WalletChangedEvent {
    private final List<WalletNode> historyChangedNodes;

    public WalletHistoryChangedEvent(Wallet wallet, List<WalletNode> historyChangedNodes) {
        super(wallet);
        this.historyChangedNodes = historyChangedNodes;
    }

    public List<WalletNode> getHistoryChangedNodes() {
        return historyChangedNodes;
    }
}
