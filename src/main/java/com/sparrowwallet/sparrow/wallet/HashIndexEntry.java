package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.DateLabel;
import com.sparrowwallet.sparrow.event.WalletEntryLabelChangedEvent;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HashIndexEntry extends Entry implements Comparable<HashIndexEntry> {
    private final Wallet wallet;
    private final BlockTransactionHashIndex hashIndex;
    private final Type type;
    private final KeyPurpose keyPurpose;

    public HashIndexEntry(Wallet wallet, BlockTransactionHashIndex hashIndex, Type type, KeyPurpose keyPurpose) {
        super(hashIndex.getLabel(), hashIndex.getSpentBy() != null ? List.of(new HashIndexEntry(wallet, hashIndex.getSpentBy(), Type.INPUT, keyPurpose)) : Collections.emptyList());
        this.wallet = wallet;
        this.hashIndex = hashIndex;
        this.type = type;
        this.keyPurpose = keyPurpose;

        labelProperty().addListener((observable, oldValue, newValue) -> {
            hashIndex.setLabel(newValue);
            EventManager.get().post(new WalletEntryLabelChangedEvent(wallet, this));
        });
    }

    public Wallet getWallet() {
        return wallet;
    }

    public BlockTransactionHashIndex getHashIndex() {
        return hashIndex;
    }

    public Type getType() {
        return type;
    }

    public KeyPurpose getKeyPurpose() {
        return keyPurpose;
    }

    public BlockTransaction getBlockTransaction() {
        return wallet.getTransactions().get(hashIndex.getHash());
    }

    public String getDescription() {
        return (type.equals(Type.INPUT) ? "Spent by input " : "Received from output ") +
                getHashIndex().getHash().toString().substring(0, 8) + "..:" +
                getHashIndex().getIndex() +
                " on " + DateLabel.getShortDateFormat(getHashIndex().getDate());
    }

    public boolean isSpent() {
        return getType().equals(HashIndexEntry.Type.INPUT) || getHashIndex().getSpentBy() != null;
    }

    @Override
    public Long getValue() {
        return hashIndex.getValue();
    }

    public enum Type {
        INPUT, OUTPUT
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HashIndexEntry)) return false;
        HashIndexEntry that = (HashIndexEntry) o;
        return wallet.equals(that.wallet) &&
                hashIndex.equals(that.hashIndex) &&
                type == that.type &&
                keyPurpose == that.keyPurpose;
    }

    @Override
    public int hashCode() {
        return Objects.hash(wallet, hashIndex, type, keyPurpose);
    }

    @Override
    public int compareTo(HashIndexEntry o) {
        if(!getType().equals(o.getType())) {
            return o.getType().ordinal() - getType().ordinal();
        }

        if(getHashIndex().getHeight() != o.getHashIndex().getHeight()) {
            return o.getHashIndex().getHeight() - getHashIndex().getHeight();
        }

        return (int)o.getHashIndex().getIndex() - (int)getHashIndex().getIndex();
    }
}
