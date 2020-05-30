package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.stream.Collectors;

public class NodeEntry extends Entry {
    private final Wallet wallet;
    private final Wallet.Node node;

    public NodeEntry(Wallet wallet, Wallet.Node node) {
        super(node.getLabel(), node.getChildren().stream().map(childNode -> new NodeEntry(wallet, childNode)).collect(Collectors.toList()));
        this.wallet = wallet;
        this.node = node;

        labelProperty().addListener((observable, oldValue, newValue) -> node.setLabel(newValue));
    }

    public Address getAddress() {
        return wallet.getAddress(node);
    }

    public Script getOutputScript() {
        return wallet.getOutputScript(node);
    }

    public String getOutputDescriptor() {
        return wallet.getOutputDescriptor(node);
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
