package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class UtxoEntry extends HashIndexEntry {
    private final WalletNode node;

    public UtxoEntry(Wallet wallet, BlockTransactionHashIndex hashIndex, Type type, WalletNode node) {
        super(wallet, hashIndex, type, node.getKeyPurpose());
        this.node = node;
    }

    @Override
    public ObservableList<Entry> getChildren() {
        return FXCollections.emptyObservableList();
    }

    @Override
    public String getDescription() {
        return getHashIndex().getHash().toString().substring(0, 8) + "...:" + getHashIndex().getIndex();
    }

    @Override
    public boolean isSpent() {
        return false;
    }

    public Address getAddress() {
        return getWallet().getAddress(node);
    }

    public WalletNode getNode() {
        return node;
    }

    public String getOutputDescriptor() {
        return getWallet().getOutputDescriptor(node);
    }
}
