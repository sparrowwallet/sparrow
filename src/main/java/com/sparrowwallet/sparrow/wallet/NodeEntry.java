package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletEntryLabelChangedEvent;
import com.sparrowwallet.sparrow.io.Config;

import java.util.stream.Collectors;

public class NodeEntry extends Entry implements Comparable<NodeEntry> {
    private final WalletNode node;

    public NodeEntry(Wallet wallet, WalletNode node) {
        super(wallet, node.getLabel(),
                !node.getChildren().isEmpty() ?
                        node.getChildren().stream().filter(childNode -> !Config.get().isHideEmptyUsedAddresses() || childNode.getTransactionOutputs().isEmpty() || !childNode.getUnspentTransactionOutputs().isEmpty()).map(childNode -> new NodeEntry(wallet, childNode)).collect(Collectors.toList()) :
                        node.getTransactionOutputs().stream().map(txo -> new HashIndexEntry(wallet, txo, HashIndexEntry.Type.OUTPUT, node.getKeyPurpose())).collect(Collectors.toList()));

        this.node = node;

        labelProperty().addListener((observable, oldValue, newValue) -> {
            node.setLabel(newValue);
            EventManager.get().post(new WalletEntryLabelChangedEvent(wallet, this));
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

    @Override
    public Long getValue() {
        if(node.getTransactionOutputs().isEmpty()) {
            return null;
        }

        return node.getUnspentValue();
    }

    @Override
    public int compareTo(NodeEntry other) {
        return node.compareTo(other.node);
    }
}
