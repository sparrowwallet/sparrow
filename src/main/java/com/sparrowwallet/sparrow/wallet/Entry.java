package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;

public abstract class Entry {
    private final Wallet wallet;
    private final SimpleStringProperty labelProperty;
    private final ObservableList<Entry> children;

    public Entry(Wallet wallet, String label, List<Entry> entries) {
        this.wallet = wallet;
        this.labelProperty = new SimpleStringProperty(this, "label", label);
        this.children = FXCollections.observableList(entries);
    }

    public Entry(Wallet wallet, SimpleStringProperty labelProperty, ObservableList<Entry> children) {
        this.wallet = wallet;
        this.labelProperty = labelProperty;
        this.children = children;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getLabel() {
        return labelProperty.get();
    }

    public SimpleStringProperty labelProperty() {
        return labelProperty;
    }

    public ObservableList<Entry> getChildren() {
        return children;
    }

    public abstract Long getValue();

    public abstract String getEntryType();

    public abstract Function getWalletFunction();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entry)) return false;
        Entry entry = (Entry) o;
        return wallet.equals(entry.wallet)
                || (wallet.isNested() && entry.wallet.getChildWallets().contains(wallet))
                || (entry.wallet.isNested() && wallet.getChildWallets().contains(entry.wallet));
    }

    public void updateLabels(Collection<Entry> entries) {
        Map<Object, List<Entry>> entriesByKey = new HashMap<>();
        for(Entry entry : entries) {
            entriesByKey.computeIfAbsent(entry.getLabelKey(), key -> new ArrayList<>()).add(entry);
        }

        updateLabels(entriesByKey);
    }

    private void updateLabels(Map<Object, List<Entry>> entriesByKey) {
        for(Entry entry : entriesByKey.getOrDefault(getLabelKey(), Collections.emptyList())) {
            if(this.equals(entry)) {
                labelProperty.set(entry.getLabel());
            }
        }

        for(Entry child : getChildren()) {
            child.updateLabels(entriesByKey);
        }
    }

    //Entries are equal across nested wallets and regardless of child count, so they are grouped by what they label and compared within each group
    private Object getLabelKey() {
        return switch(this) {
            case TransactionEntry transactionEntry -> transactionEntry.getBlockTransaction().getHash();
            case HashIndexEntry hashIndexEntry -> hashIndexEntry.getHashIndex().getHash();
            case NodeEntry nodeEntry -> nodeEntry.getNode();
            default -> getClass();
        };
    }
}
