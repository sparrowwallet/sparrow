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
        return getWallet().getAddress(node);
    }

    public Script getOutputScript() {
        return getWallet().getOutputScript(node);
    }

    public String getOutputDescriptor() {
        return getWallet().getOutputDescriptor(node);
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

    public Set<Entry> copyLabels(WalletNode pastNode) {
        if(pastNode == null) {
            return Collections.emptySet();
        }

        Set<Entry> changedEntries = new LinkedHashSet<>();

        if(node.getLabel() == null && pastNode.getLabel() != null) {
            node.setLabel(pastNode.getLabel());
            labelProperty().set(pastNode.getLabel());
            changedEntries.add(this);
        }

        for(Entry childEntry : getChildren()) {
            if(childEntry instanceof HashIndexEntry) {
                HashIndexEntry hashIndexEntry = (HashIndexEntry)childEntry;
                BlockTransactionHashIndex txo = hashIndexEntry.getHashIndex();
                Optional<BlockTransactionHashIndex> optPastTxo = pastNode.getTransactionOutputs().stream().filter(pastTxo -> pastTxo.equals(txo)).findFirst();
                if(optPastTxo.isPresent()) {
                    BlockTransactionHashIndex pastTxo = optPastTxo.get();
                    if(txo.getLabel() == null && pastTxo.getLabel() != null) {
                        txo.setLabel(pastTxo.getLabel());
                        changedEntries.add(childEntry);
                    }
                    if(txo.isSpent() && pastTxo.isSpent() && txo.getSpentBy().getLabel() == null && pastTxo.getSpentBy().getLabel() != null) {
                        txo.getSpentBy().setLabel(pastTxo.getSpentBy().getLabel());
                        changedEntries.add(childEntry);
                    }
                }
            }

            if(childEntry instanceof NodeEntry) {
                NodeEntry childNodeEntry = (NodeEntry)childEntry;
                Optional<WalletNode> optPastChildNodeEntry = pastNode.getChildren().stream().filter(childNodeEntry.node::equals).findFirst();
                optPastChildNodeEntry.ifPresent(pastChildNode -> changedEntries.addAll(childNodeEntry.copyLabels(pastChildNode)));
            }
        }

        return changedEntries;
    }
}
