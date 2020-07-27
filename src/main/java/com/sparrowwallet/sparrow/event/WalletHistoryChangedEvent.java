package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This is posted by WalletForm once the history of the wallet has been refreshed, and new transactions detected
 *
 */
public class WalletHistoryChangedEvent extends WalletChangedEvent {
    private final List<WalletNode> historyChangedNodes;

    public WalletHistoryChangedEvent(Wallet wallet, List<WalletNode> historyChangedNodes) {
        super(wallet);
        this.historyChangedNodes = historyChangedNodes;
    }

    public List<WalletNode> getHistoryChangedNodes() {
        return historyChangedNodes;
    }

    public List<WalletNode> getReceiveNodes() {
        return getWallet().getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(historyChangedNodes::contains).collect(Collectors.toList());
    }

    public List<WalletNode> getChangeNodes() {
        return getWallet().getNode(KeyPurpose.CHANGE).getChildren().stream().filter(historyChangedNodes::contains).collect(Collectors.toList());
    }
}
