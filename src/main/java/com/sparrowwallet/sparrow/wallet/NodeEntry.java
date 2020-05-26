package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;

public class NodeEntry extends Entry {
    private final Wallet.Node node;

    public NodeEntry(Wallet.Node node) {
        super(node.getLabel());
        this.node = node;

        labelProperty().addListener((observable, oldValue, newValue) -> node.setLabel(newValue));
    }

    @Override
    public Long getAmount() {
        //TODO: Iterate through TransactionEntries to calculate amount

        return null;
    }

    public Wallet.Node getNode() {
        return node;
    }
}
