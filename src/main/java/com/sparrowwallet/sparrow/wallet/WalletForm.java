package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WalletForm {
    private static final Logger log = LoggerFactory.getLogger(WalletForm.class);

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

    public void setWallet(Wallet wallet) {
        throw new UnsupportedOperationException("Only SettingsWalletForm supports setWallet");
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

    public void saveBackup() throws IOException {
        storage.backupWallet();
    }

    public void refreshHistory(Integer blockHeight) {
        refreshHistory(blockHeight, null);
    }

    public void refreshHistory(Integer blockHeight, WalletNode node) {
        Wallet previousWallet = wallet.copy();
        if(wallet.isValid() && AppController.isOnline()) {
            log.debug(node == null ? "Refreshing full wallet history" : "Requesting node wallet history for " + node.getDerivationPath());
            ElectrumServer.TransactionHistoryService historyService = new ElectrumServer.TransactionHistoryService(wallet, node);
            historyService.setOnSucceeded(workerStateEvent -> {
                EventManager.get().post(new WalletHistoryStatusEvent(true));
                updateWallet(previousWallet, blockHeight);
            });
            historyService.setOnFailed(workerStateEvent -> {
                log.error("Error retrieving wallet history", workerStateEvent.getSource().getException());
                EventManager.get().post(new WalletHistoryStatusEvent(workerStateEvent.getSource().getException().getMessage()));
            });
            EventManager.get().post(new WalletHistoryStatusEvent(false));
            historyService.start();
        }
    }

    private void updateWallet(Wallet previousWallet, Integer blockHeight) {
        if(blockHeight != null) {
            wallet.setStoredBlockHeight(blockHeight);
        }

        notifyIfChanged(previousWallet, blockHeight);
    }

    private void notifyIfChanged(Wallet previousWallet, Integer blockHeight) {
        List<WalletNode> historyChangedNodes = new ArrayList<>();
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getNode(KeyPurpose.RECEIVE).getChildren(), wallet.getNode(KeyPurpose.RECEIVE).getChildren()));
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getNode(KeyPurpose.CHANGE).getChildren(), wallet.getNode(KeyPurpose.CHANGE).getChildren()));

        boolean changed = false;
        if(!historyChangedNodes.isEmpty()) {
            Platform.runLater(() -> EventManager.get().post(new WalletHistoryChangedEvent(wallet, historyChangedNodes)));
            changed = true;
        }

        if(blockHeight != null && !blockHeight.equals(previousWallet.getStoredBlockHeight())) {
            Platform.runLater(() -> EventManager.get().post(new WalletBlockHeightChangedEvent(wallet, blockHeight)));
            changed = true;
        }

        if(changed) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
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
    public void walletDataChanged(WalletDataChangedEvent event) {
        if(event.getWallet().equals(wallet)) {
            backgroundSaveWallet();
        }
    }

    private void backgroundSaveWallet() {
        try {
            save();
        } catch (IOException e) {
            //Background save failed
            log.error("Background wallet save failed", e);
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
        //Check if wallet is valid to avoid saving wallets in initial setup
        if(wallet.isValid()) {
            updateWallet(wallet.copy(), event.getHeight());
        }
    }

    @Subscribe
    public void connected(ConnectionEvent event) {
        refreshHistory(event.getBlockHeight());
    }

    @Subscribe
    public void walletNodeHistoryChanged(WalletNodeHistoryChangedEvent event) {
        if(wallet.isValid()) {
            WalletNode walletNode = event.getWalletNode(wallet);
            if(walletNode != null) {
                log.debug(wallet.getName() + " history event for node " + walletNode);
                refreshHistory(AppController.getCurrentBlockHeight(), walletNode);
            }
        }
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        for(WalletTabData tabData : event.getClosedWalletTabData()) {
            if(tabData.getWalletForm() == this) {
                EventManager.get().unregister(this);
            }
        }
    }
}
