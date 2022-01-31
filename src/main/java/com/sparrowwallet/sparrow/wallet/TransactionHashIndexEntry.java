package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class TransactionHashIndexEntry extends HashIndexEntry {
    public TransactionHashIndexEntry(Wallet wallet, BlockTransactionHashIndex hashIndex, Type type, KeyPurpose keyPurpose) {
        super(wallet, hashIndex, type, keyPurpose);
    }

    @Override
    public ObservableList<Entry> getChildren() {
        return FXCollections.emptyObservableList();
    }

    @Override
    public String getDescription() {
        if(getType().equals(Type.INPUT)) {
            TransactionInput txInput = getBlockTransaction().getTransaction().getInputs().get((int)getHashIndex().getIndex());
            return "Spent " + txInput.getOutpoint().getHash().toString().substring(0, 8) + "..:" + txInput.getOutpoint().getIndex();
        } else {
            return (getKeyPurpose().equals(KeyPurpose.RECEIVE) ? "Received to " : "Change to ") + getHashIndex().getHash().toString().substring(0, 8) + "..:" + getHashIndex().getIndex();
        }
    }

    @Override
    public boolean isSpent() {
        return false;
    }

    @Override
    public Function getWalletFunction() {
        return Function.TRANSACTIONS;
    }
}
