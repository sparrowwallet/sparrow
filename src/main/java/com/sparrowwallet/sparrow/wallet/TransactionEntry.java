package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletChangedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class TransactionEntry extends Entry implements Comparable<TransactionEntry> {
    private static final int BLOCKS_TO_CONFIRM = 6;

    private final Wallet wallet;
    private final BlockTransaction blockTransaction;
    private WalletTransactionsEntry parent;

    public TransactionEntry(Wallet wallet, BlockTransaction blockTransaction, Map<BlockTransactionHashIndex, KeyPurpose> inputs, Map<BlockTransactionHashIndex, KeyPurpose> outputs) {
        super(blockTransaction.getLabel(), createChildEntries(wallet, inputs, outputs));
        this.wallet = wallet;
        this.blockTransaction = blockTransaction;

        labelProperty().addListener((observable, oldValue, newValue) -> {
            blockTransaction.setLabel(newValue);
            EventManager.get().post(new WalletChangedEvent(wallet));
        });
    }

    public Wallet getWallet() {
        return wallet;
    }

    void setParent(WalletTransactionsEntry walletTransactionsEntry) {
        this.parent = walletTransactionsEntry;
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

    public Long getBalance() {
        return parent.getBalance(this);
    }

    public boolean isConfirming() {
        return getConfirmations() < BLOCKS_TO_CONFIRM;
    }

    public int getConfirmations() {
        if(blockTransaction.getHeight() == 0) {
            return 0;
        }

        return wallet.getStoredBlockHeight() - blockTransaction.getHeight() + 1;
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
                blockTransaction.equals(that.blockTransaction) &&
                parent.equals(that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wallet, blockTransaction, parent);
    }

    @Override
    public int compareTo(@NotNull TransactionEntry other) {
        return blockTransaction.compareTo(other.blockTransaction);
    }
}
