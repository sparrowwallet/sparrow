package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.io.Storage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is posted by WalletForm once the history of the wallet has been refreshed, and new transactions detected
 *
 */
public class WalletHistoryChangedEvent extends WalletChangedEvent {
    private final Storage storage;
    private final List<WalletNode> historyChangedNodes;
    private final List<WalletNode> nestedHistoryChangedNodes;

    public WalletHistoryChangedEvent(Wallet wallet, Storage storage, List<WalletNode> historyChangedNodes, List<WalletNode> nestedHistoryChangedNodes) {
        super(wallet);
        this.storage = storage;
        this.historyChangedNodes = historyChangedNodes;
        this.nestedHistoryChangedNodes = nestedHistoryChangedNodes;
    }

    public String getWalletId() {
        return storage.getWalletId(getWallet());
    }

    public List<WalletNode> getHistoryChangedNodes() {
        return historyChangedNodes;
    }

    public List<WalletNode> getNestedHistoryChangedNodes() {
        return nestedHistoryChangedNodes;
    }

    public List<WalletNode> getAllHistoryChangedNodes() {
        List<WalletNode> allHistoryChangedNodes = new ArrayList<>(historyChangedNodes.size() + nestedHistoryChangedNodes.size());
        allHistoryChangedNodes.addAll(historyChangedNodes);
        allHistoryChangedNodes.addAll(nestedHistoryChangedNodes);
        return allHistoryChangedNodes;
    }

    public List<WalletNode> getReceiveNodes() {
        return historyChangedNodes.stream().filter(node -> node.getKeyPurpose() == KeyPurpose.RECEIVE).collect(Collectors.toList());
    }

    public List<WalletNode> getChangeNodes() {
        return historyChangedNodes.stream().filter(node -> node.getKeyPurpose() == KeyPurpose.CHANGE).collect(Collectors.toList());
    }
}
