package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
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
        this.oldWallet = currentWallet;
        this.wallet = currentWallet.copy();
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
        this.wallet = oldWallet.copy();
    }

    public void save() throws IOException {
        storage.storeWallet(wallet);
        oldWallet = wallet.copy();
    }

    public NodeEntry getNodeEntry(KeyPurpose keyPurpose) {
        NodeEntry purposeEntry;
        Optional<NodeEntry> optionalPurposeEntry = accountEntries.stream().filter(entry -> entry.getNode().getKeyPurpose().equals(keyPurpose)).findFirst();
        if(optionalPurposeEntry.isPresent()) {
            purposeEntry = optionalPurposeEntry.get();
        } else {
            Wallet.Node purposeNode = getWallet().getNode(keyPurpose);
            purposeEntry = new NodeEntry(purposeNode);
            accountEntries.add(purposeEntry);
        }

        return purposeEntry;
    }

    public NodeEntry getFreshNodeEntry(KeyPurpose keyPurpose, NodeEntry currentEntry) {
        NodeEntry rootEntry = getNodeEntry(keyPurpose);
        Wallet.Node freshNode = getWallet().getFreshNode(keyPurpose, currentEntry == null ? null : currentEntry.getNode());

        for(Entry childEntry : rootEntry.getChildren()) {
            NodeEntry nodeEntry = (NodeEntry)childEntry;
            if(nodeEntry.getNode().equals(freshNode)) {
                return nodeEntry;
            }
        }

        NodeEntry freshEntry = new NodeEntry(freshNode);
        rootEntry.getChildren().add(freshEntry);
        return freshEntry;
    }
}
