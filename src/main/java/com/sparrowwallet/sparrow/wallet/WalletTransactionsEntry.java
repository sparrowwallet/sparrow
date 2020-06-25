package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import javafx.beans.property.LongProperty;
import javafx.beans.property.LongPropertyBase;

import java.util.*;
import java.util.stream.Collectors;

public class WalletTransactionsEntry extends Entry {
    private final Wallet wallet;

    public WalletTransactionsEntry(Wallet wallet) {
        super(wallet.getName(), getWalletTransactions(wallet).stream().map(WalletTransaction::getTransactionEntry).collect(Collectors.toList()));
        this.wallet = wallet;
        calculateBalances();
    }

    @Override
    public Long getValue() {
        return getBalance();
    }

    protected void calculateBalances() {
        long balance = 0L;

        //Note transaction entries must be in ascending order. This sorting is ultimately done according to BlockTransactions' comparator
        getChildren().sort(Comparator.comparing(TransactionEntry.class::cast));

        for(Entry entry : getChildren()) {
            TransactionEntry transactionEntry = (TransactionEntry)entry;
            balance += entry.getValue();
            transactionEntry.setBalance(balance);
        }

        setBalance(balance);
    }

    public void updateTransactions() {
        List<Entry> current = getWalletTransactions(wallet).stream().map(WalletTransaction::getTransactionEntry).collect(Collectors.toList());
        List<Entry> previous = new ArrayList<>(getChildren());

        List<Entry> entriesAdded = new ArrayList<>(current);
        entriesAdded.removeAll(previous);
        getChildren().addAll(entriesAdded);

        List<Entry> entriesRemoved = new ArrayList<>(previous);
        entriesRemoved.removeAll(current);
        getChildren().removeAll(entriesRemoved);

        calculateBalances();
    }

    private static Collection<WalletTransaction> getWalletTransactions(Wallet wallet) {
        Map<BlockTransaction, WalletTransaction> walletTransactionMap = new TreeMap<>();

        getWalletTransactions(wallet, walletTransactionMap, wallet.getNode(KeyPurpose.RECEIVE));
        getWalletTransactions(wallet, walletTransactionMap, wallet.getNode(KeyPurpose.CHANGE));

        return new ArrayList<>(walletTransactionMap.values());
    }

    private static void getWalletTransactions(Wallet wallet, Map<BlockTransaction, WalletTransaction> walletTransactionMap, WalletNode purposeNode) {
        KeyPurpose keyPurpose = purposeNode.getKeyPurpose();
        for(WalletNode addressNode : purposeNode.getChildren()) {
            for(BlockTransactionHashIndex hashIndex : addressNode.getTransactionOutputs()) {
                BlockTransaction inputTx = wallet.getTransactions().get(hashIndex.getHash());
                WalletTransaction inputWalletTx = walletTransactionMap.get(inputTx);
                if(inputWalletTx == null) {
                    inputWalletTx = new WalletTransaction(wallet, inputTx);
                    walletTransactionMap.put(inputTx, inputWalletTx);
                }
                inputWalletTx.incoming.put(hashIndex, keyPurpose);

                if(hashIndex.getSpentBy() != null) {
                    BlockTransaction outputTx = wallet.getTransactions().get(hashIndex.getSpentBy().getHash());
                    WalletTransaction outputWalletTx = walletTransactionMap.get(outputTx);
                    if(outputWalletTx == null) {
                        outputWalletTx = new WalletTransaction(wallet, outputTx);
                        walletTransactionMap.put(outputTx, outputWalletTx);
                    }
                    outputWalletTx.outgoing.put(hashIndex.getSpentBy(), keyPurpose);
                }
            }
        }
    }

    /**
     * Defines the wallet balance in total.
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
                    return WalletTransactionsEntry.this;
                }

                @Override
                public String getName() {
                    return "balance";
                }
            };
        }
        return balance;
    }

    private static class WalletTransaction {
        private final Wallet wallet;
        private final BlockTransaction blockTransaction;
        private final Map<BlockTransactionHashIndex, KeyPurpose> incoming = new TreeMap<>();
        private final Map<BlockTransactionHashIndex, KeyPurpose> outgoing = new TreeMap<>();

        public WalletTransaction(Wallet wallet, BlockTransaction blockTransaction) {
            this.wallet = wallet;
            this.blockTransaction = blockTransaction;
        }

        public TransactionEntry getTransactionEntry() {
            return new TransactionEntry(wallet, blockTransaction, incoming, outgoing);
        }
    }
}
