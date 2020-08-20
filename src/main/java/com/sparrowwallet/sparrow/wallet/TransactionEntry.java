package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.WalletBlockHeightChangedEvent;
import com.sparrowwallet.sparrow.event.WalletEntryLabelChangedEvent;
import com.sparrowwallet.sparrow.event.WalletTabsClosedEvent;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.IntegerPropertyBase;
import javafx.beans.property.LongProperty;
import javafx.beans.property.LongPropertyBase;

import java.util.*;
import java.util.stream.Collectors;

public class TransactionEntry extends Entry implements Comparable<TransactionEntry> {
    private final Wallet wallet;
    private final BlockTransaction blockTransaction;

    public TransactionEntry(Wallet wallet, BlockTransaction blockTransaction, Map<BlockTransactionHashIndex, KeyPurpose> inputs, Map<BlockTransactionHashIndex, KeyPurpose> outputs) {
        super(blockTransaction.getLabel(), createChildEntries(wallet, inputs, outputs));
        this.wallet = wallet;
        this.blockTransaction = blockTransaction;

        labelProperty().addListener((observable, oldValue, newValue) -> {
            blockTransaction.setLabel(newValue);
            EventManager.get().post(new WalletEntryLabelChangedEvent(wallet, this));
        });

        setConfirmations(calculateConfirmations());
        if(isFullyConfirming()) {
            EventManager.get().register(this);
        }
    }

    public Wallet getWallet() {
        return wallet;
    }

    public BlockTransaction getBlockTransaction() {
        return blockTransaction;
    }

    @Override
    public Long getValue() {
        long value = 0L;
        for(Entry entry : getChildren()) {
            HashIndexEntry hashIndexEntry = (HashIndexEntry)entry;
            if(hashIndexEntry.getType().equals(HashIndexEntry.Type.INPUT)) {
                value -= hashIndexEntry.getValue();
            } else {
                value += hashIndexEntry.getValue();
            }
        }

        return value;
    }

    public boolean isConfirming() {
        return getConfirmations() < BlockTransactionHash.BLOCKS_TO_CONFIRM;
    }

    public boolean isFullyConfirming() {
        return getConfirmations() < BlockTransactionHash.BLOCKS_TO_FULLY_CONFIRM;
    }

    public int calculateConfirmations() {
        return blockTransaction.getConfirmations(wallet.getStoredBlockHeight());
    }

    public String getConfirmationsDescription() {
        int confirmations = getConfirmations();
        if(confirmations == 0) {
            return "Unconfirmed in mempool";
        } else if(confirmations < BlockTransactionHash.BLOCKS_TO_FULLY_CONFIRM) {
            return confirmations + " confirmation" + (confirmations == 1 ? "" : "s");
        } else {
            return BlockTransactionHash.BLOCKS_TO_FULLY_CONFIRM + "+ confirmations";
        }
    }

    private static List<Entry> createChildEntries(Wallet wallet, Map<BlockTransactionHashIndex, KeyPurpose> incoming, Map<BlockTransactionHashIndex, KeyPurpose> outgoing) {
        List<Entry> incomingOutputEntries = incoming.entrySet().stream().map(input -> new TransactionHashIndexEntry(wallet, input.getKey(), HashIndexEntry.Type.OUTPUT, input.getValue())).collect(Collectors.toList());
        List<Entry> outgoingInputEntries = outgoing.entrySet().stream().map(output -> new TransactionHashIndexEntry(wallet, output.getKey(), HashIndexEntry.Type.INPUT, output.getValue())).collect(Collectors.toList());

        List<Entry> childEntries = new ArrayList<>();
        childEntries.addAll(incomingOutputEntries);
        childEntries.addAll(outgoingInputEntries);

        childEntries.sort((o1, o2) -> {
            TransactionHashIndexEntry entry1 = (TransactionHashIndexEntry) o1;
            TransactionHashIndexEntry entry2 = (TransactionHashIndexEntry) o2;

            if (!entry1.getHashIndex().getHash().equals(entry2.getHashIndex().getHash())) {
                return entry1.getHashIndex().getHash().compareTo(entry2.getHashIndex().getHash());
            }

            if (!entry1.getType().equals(entry2.getType())) {
                return entry1.getType().ordinal() - entry2.getType().ordinal();
            }

            return (int) entry1.getHashIndex().getIndex() - (int) entry2.getHashIndex().getIndex();
        });

        return childEntries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionEntry that = (TransactionEntry) o;
        return wallet.equals(that.wallet) &&
                blockTransaction.equals(that.blockTransaction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wallet, blockTransaction);
    }

    @Override
    public int compareTo(TransactionEntry other) {
        return blockTransaction.compareTo(other.blockTransaction);
    }

    /**
     * Defines the number of confirmations
     */
    private IntegerProperty confirmations;

    public final void setConfirmations(int value) {
        if(confirmations != null || value != 0) {
            confirmationsProperty().set(value);
        }
    }

    public final int getConfirmations() {
        return confirmations == null ? 0 : confirmations.get();
    }

    public final IntegerProperty confirmationsProperty() {
        if(confirmations == null) {
            confirmations = new IntegerPropertyBase(0) {

                @Override
                public Object getBean() {
                    return TransactionEntry.this;
                }

                @Override
                public String getName() {
                    return "confirmations";
                }
            };
        }
        return confirmations;
    }

    /**
     * Defines the wallet balance at the historical point of this transaction, as defined by BlockTransaction's compareTo method.
     */
    private LongProperty balance;

    public final void setBalance(long value) {
        if(balance != null || value != 0) {
            balanceProperty().set(value);
        }
    }

    public final long getBalance() {
        return balance == null ? 0L : balance.get();
    }

    public final LongProperty balanceProperty() {
        if(balance == null) {
            balance = new LongPropertyBase(0L) {

                @Override
                public Object getBean() {
                    return TransactionEntry.this;
                }

                @Override
                public String getName() {
                    return "balance";
                }
            };
        }
        return balance;
    }

    @Subscribe
    public void blockHeightChanged(WalletBlockHeightChangedEvent event) {
        if(getWallet().equals(event.getWallet())) {
            setConfirmations(calculateConfirmations());

            if(!isFullyConfirming()) {
                EventManager.get().unregister(this);
            }
        }
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        for(WalletTabData tabData : event.getClosedWalletTabData()) {
            if(tabData.getWalletForm().getWallet() == wallet) {
                EventManager.get().unregister(this);
            }
        }
    }
}
