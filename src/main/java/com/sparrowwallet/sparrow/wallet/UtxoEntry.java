package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
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

    /**
     * Defines whether this utxo shares it's address with another utxo in the wallet
     */
    private BooleanProperty duplicateAddress;

    public final void setDuplicateAddress(boolean value) {
        if(duplicateAddress != null || value) {
            duplicateAddressProperty().set(value);
        }
    }

    public final boolean isDuplicateAddress() {
        return duplicateAddress != null && duplicateAddress.get();
    }

    public final BooleanProperty duplicateAddressProperty() {
        if(duplicateAddress == null) {
            duplicateAddress = new BooleanPropertyBase(false) {

                @Override
                public Object getBean() {
                    return UtxoEntry.this;
                }

                @Override
                public String getName() {
                    return "duplicate";
                }
            };
        }
        return duplicateAddress;
    }
}
