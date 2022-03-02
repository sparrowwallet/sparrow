package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

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

    public void updateLabel(Entry entry) {
        if(this.equals(entry)) {
            labelProperty.set(entry.getLabel());
        }

        for(Entry child : getChildren()) {
            child.updateLabel(entry);
        }
    }
}
