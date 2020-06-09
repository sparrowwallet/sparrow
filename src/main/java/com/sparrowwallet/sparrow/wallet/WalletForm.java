package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.WalletChangedEvent;
import com.sparrowwallet.sparrow.io.ElectrumServer;
import com.sparrowwallet.sparrow.io.Storage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WalletForm {
    private final Storage storage;
    private Wallet oldWallet;
    private Wallet wallet;

    private final List<NodeEntry> accountEntries = new ArrayList<>();

    public WalletForm(Storage storage, Wallet currentWallet) {
        this.storage = storage;
        this.oldWallet = currentWallet.copy();
        this.wallet = currentWallet;
        refreshHistory();

        EventManager.get().register(this);
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

    public void revertAndRefresh() {
        this.wallet = oldWallet.copy();
        refreshHistory();
    }

    public void save() throws IOException {
        storage.storeWallet(wallet);
        oldWallet = wallet.copy();
    }

    public void saveAndRefresh() throws IOException {
        //TODO: Detect trivial changes and don't clear history
        wallet.clearHistory();
        save();
        refreshHistory();
    }

    public void refreshHistory() {
        if(wallet.isValid()) {
            ElectrumServer.TransactionHistoryService historyService = new ElectrumServer.TransactionHistoryService(wallet);
            historyService.setOnSucceeded(workerStateEvent -> {
                //TODO: Show connected
                try {
                    storage.storeWallet(wallet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            historyService.setOnFailed(workerStateEvent -> {
                //TODO: Show not connected, log exception
            });
            historyService.start();
        }
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

    @Subscribe
    public void walletChanged(WalletChangedEvent event) {
        if(event.getWallet().equals(wallet)) {
            try {
                save();
            } catch (IOException e) {
                //Background save failed
                e.printStackTrace();
            }
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        refreshHistory();
    }
}
