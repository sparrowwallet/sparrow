package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletEntryLabelsChangedEvent;
import com.sparrowwallet.sparrow.io.Config;

import java.util.*;
import java.util.stream.Collectors;

public class NodeEntry extends Entry implements Comparable<NodeEntry> {
    private final WalletNode node;

    public NodeEntry(Wallet wallet, WalletNode node) {
        super(wallet, node.getLabel(), createChildren(wallet, node));
        this.node = node;

        labelProperty().addListener((observable, oldValue, newValue) -> {
            if(!Objects.equals(node.getLabel(), newValue)) {
                node.setLabel(newValue);
                EventManager.get().post(new WalletEntryLabelsChangedEvent(wallet, this));
            }
        });
    }

    public WalletNode getNode() {
        return node;
    }

    public Address getAddress() {
        return node.getAddress();
    }

    public Script getOutputScript() {
        return node.getOutputScript();
    }

    public String getOutputDescriptor() {
        return node.getOutputDescriptor();
    }

    public void refreshChildren() {
        getChildren().clear();
        getChildren().addAll(createChildren(getWallet(), node));
    }

    private static List<Entry> createChildren(Wallet wallet, WalletNode node) {
        return !node.getChildren().isEmpty() ?
                node.getChildren().stream().filter(childNode -> !Config.get().isHideEmptyUsedAddresses() || childNode.getTransactionOutputs().isEmpty() || !childNode.getUnspentTransactionOutputs().isEmpty()).map(childNode -> new NodeEntry(wallet, childNode)).collect(Collectors.toList()) :
                node.getTransactionOutputs().stream().map(txo -> new HashIndexEntry(wallet, txo, HashIndexEntry.Type.OUTPUT, node.getKeyPurpose())).collect(Collectors.toList());
    }

    @Override
    public Long getValue() {
        if(node.getTransactionOutputs().isEmpty()) {
            return null;
        }

        return node.getUnspentValue();
    }

    @Override
    public String getEntryType() {
        return "Address";
    }

    @Override
    public Function getWalletFunction() {
        return Function.ADDRESSES;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeEntry that = (NodeEntry) o;
        return getWallet().equals(that.getWallet()) && node.equals(that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getWallet(), node);
    }

    @Override
    public int compareTo(NodeEntry other) {
        return node.compareTo(other.node);
    }
}
