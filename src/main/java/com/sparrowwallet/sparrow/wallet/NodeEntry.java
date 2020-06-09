package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletChangedEvent;

import java.util.stream.Collectors;

public class NodeEntry extends Entry {
    private final Wallet wallet;
    private final WalletNode node;

    public NodeEntry(Wallet wallet, WalletNode node) {
        super(node.getLabel(),
                !node.getChildren().isEmpty() ?
                        node.getChildren().stream().map(childNode -> new NodeEntry(wallet, childNode)).collect(Collectors.toList()) :
                        node.getTransactionOutputs().stream().map(txo -> new HashIndexEntry(wallet, txo, HashIndexEntry.Type.OUTPUT)).collect(Collectors.toList()));

        this.wallet = wallet;
        this.node = node;

        labelProperty().addListener((observable, oldValue, newValue) -> {
            node.setLabel(newValue);
            EventManager.get().post(new WalletChangedEvent(wallet));
        });
    }

    public Wallet getWallet() {
        return wallet;
    }

    public WalletNode getNode() {
        return node;
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
    public Long getValue() {
        if(node.getTransactionOutputs().isEmpty()) {
            return null;
        }

        return node.getUnspentValue();
    }
}
