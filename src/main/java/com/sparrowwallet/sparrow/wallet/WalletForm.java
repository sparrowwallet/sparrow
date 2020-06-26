package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.ElectrumServer;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WalletForm {
    private final Storage storage;
    protected Wallet wallet;

    private WalletTransactionsEntry walletTransactionsEntry;
    private WalletUtxosEntry walletUtxosEntry;
    private final List<NodeEntry> accountEntries = new ArrayList<>();

    public WalletForm(Storage storage, Wallet currentWallet) {
        this.storage = storage;
        this.wallet = currentWallet;
        refreshHistory(AppController.getCurrentBlockHeight());
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Storage getStorage() {
        return storage;
    }

    public File getWalletFile() {
        return storage.getWalletFile();
    }

    public void revert() {
        throw new UnsupportedOperationException("Only SettingsWalletForm supports revert");
    }

    public void save() throws IOException {
        storage.storeWallet(wallet);
    }

    public void saveAndRefresh() throws IOException {
        wallet.clearHistory();
        save();
        refreshHistory(AppController.getCurrentBlockHeight());
    }

    public void refreshHistory(Integer blockHeight) {
        Wallet previousWallet = wallet.copy();
        if(wallet.isValid() && AppController.isOnline()) {
            ElectrumServer.TransactionHistoryService historyService = new ElectrumServer.TransactionHistoryService(wallet);
            historyService.setOnSucceeded(workerStateEvent -> {
                wallet.setStoredBlockHeight(blockHeight);
                notifyIfChanged(previousWallet, blockHeight);
            });
            historyService.setOnFailed(workerStateEvent -> {
                workerStateEvent.getSource().getException().printStackTrace();
            });
            historyService.start();
        }
    }

    private void notifyIfChanged(Wallet previousWallet, Integer blockHeight) {
        List<WalletNode> historyChangedNodes = new ArrayList<>();
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getNode(KeyPurpose.RECEIVE).getChildren(), wallet.getNode(KeyPurpose.RECEIVE).getChildren()));
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getNode(KeyPurpose.CHANGE).getChildren(), wallet.getNode(KeyPurpose.CHANGE).getChildren()));

        if(!historyChangedNodes.isEmpty()) {
            Platform.runLater(() -> EventManager.get().post(new WalletHistoryChangedEvent(wallet, blockHeight, historyChangedNodes)));
        } else if(blockHeight != null && !blockHeight.equals(previousWallet.getStoredBlockHeight())) {
            Platform.runLater(() -> EventManager.get().post(new WalletBlockHeightChangedEvent(wallet, blockHeight)));
        }
    }

    private List<WalletNode> getHistoryChangedNodes(Set<WalletNode> previousNodes, Set<WalletNode> currentNodes) {
        List<WalletNode> changedNodes = new ArrayList<>();
        for(WalletNode currentNode : currentNodes) {
            Optional<WalletNode> optPreviousNode = previousNodes.stream().filter(node -> node.equals(currentNode)).findFirst();
            if(optPreviousNode.isPresent()) {
                WalletNode previousNode = optPreviousNode.get();
                if(!currentNode.getTransactionOutputs().equals(previousNode.getTransactionOutputs())) {
                    changedNodes.add(currentNode);
                }
            } else {
                changedNodes.add(currentNode);
            }
        }

        return changedNodes;
    }

    public NodeEntry getNodeEntry(KeyPurpose keyPurpose) {
        NodeEntry purposeEntry;
        Optional<NodeEntry> optionalPurposeEntry = accountEntries.stream().filter(entry -> entry.getNode().getKeyPurpose().equals(keyPurpose)).findFirst();
        if(optionalPurposeEntry.isPresent()) {
            purposeEntry = optionalPurposeEntry.get();
        } else {
            WalletNode purposeNode = getWallet().getNode(keyPurpose);
            purposeEntry = new NodeEntry(getWallet(), purposeNode);
            accountEntries.add(purposeEntry);
        }

        return purposeEntry;
    }

    public NodeEntry getFreshNodeEntry(KeyPurpose keyPurpose, NodeEntry currentEntry) {
        NodeEntry rootEntry = getNodeEntry(keyPurpose);
        WalletNode freshNode = getWallet().getFreshNode(keyPurpose, currentEntry == null ? null : currentEntry.getNode());

        for(Entry childEntry : rootEntry.getChildren()) {
            NodeEntry nodeEntry = (NodeEntry)childEntry;
            if(nodeEntry.getNode().equals(freshNode)) {
                return nodeEntry;
            }
        }

        NodeEntry freshEntry = new NodeEntry(getWallet(), freshNode);
        rootEntry.getChildren().add(freshEntry);
        return freshEntry;
    }

    public WalletTransactionsEntry getWalletTransactionsEntry() {
        if(walletTransactionsEntry == null) {
            walletTransactionsEntry = new WalletTransactionsEntry(wallet);
        }

        return walletTransactionsEntry;
    }

    public WalletUtxosEntry getWalletUtxosEntry() {
        if(walletUtxosEntry == null) {
            walletUtxosEntry = new WalletUtxosEntry(wallet);
        }

        return walletUtxosEntry;
    }

    @Subscribe
    public void walletLabelChanged(WalletEntryLabelChangedEvent event) {
        if(event.getWallet().equals(wallet)) {
            backgroundSaveWallet(event);
        }
    }

    @Subscribe
    public void walletBlockHeightChanged(WalletBlockHeightChangedEvent event) {
        if(event.getWallet().equals(wallet)) {
            backgroundSaveWallet(event);
        }
    }

    private void backgroundSaveWallet(WalletChangedEvent event) {
        try {
            save();
        } catch (IOException e) {
            //Background save failed
            e.printStackTrace();
        }
    }

    @Subscribe
    public void walletSettingsChanged(WalletSettingsChangedEvent event) {
        if(event.getWalletFile().equals(storage.getWalletFile())) {
            wallet = event.getWallet();
            walletTransactionsEntry = null;
            walletUtxosEntry = null;
            accountEntries.clear();
            EventManager.get().post(new WalletNodesChangedEvent(wallet));
            refreshHistory(AppController.getCurrentBlockHeight());
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        refreshHistory(event.getHeight());
    }

    @Subscribe
    public void connected(ConnectionEvent event) {
        refreshHistory(event.getBlockHeight());
    }
}
