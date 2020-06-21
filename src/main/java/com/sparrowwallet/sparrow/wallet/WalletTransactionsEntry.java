package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.*;
import java.util.stream.Collectors;

public class WalletTransactionsEntry extends Entry {
    private final Wallet wallet;

    public WalletTransactionsEntry(Wallet wallet) {
        super(wallet.getName(), getWalletTransactions(wallet).stream().map(WalletTransaction::getTransactionEntry).collect(Collectors.toList()));
        this.wallet = wallet;
        getChildren().forEach(entry -> ((TransactionEntry)entry).setParent(this));
    }

    @Override
    public Long getValue() {
        return getBalance(null);
    }

    protected Long getBalance(TransactionEntry transactionEntry) {
        long balance = 0L;
        for(Entry entry : getChildren()) {
            balance += entry.getValue();

            if(entry == transactionEntry) {
                return balance;
            }
        }

        return balance;
    }

    public void updateTransactions() {
        List<Entry> current = getWalletTransactions(wallet).stream().map(WalletTransaction::getTransactionEntry).collect(Collectors.toList());
        List<Entry> previous = new ArrayList<>(getChildren());
        for(Entry currentEntry : current) {
            int index = previous.indexOf(currentEntry);
            if (index > -1) {
                getChildren().set(index, currentEntry);
            } else {
                getChildren().add(currentEntry);
            }
        }

        getChildren().sort(Comparator.comparing(TransactionEntry.class::cast));
    }

    private static Collection<WalletTransaction> getWalletTransactions(Wallet wallet) {
        Map<BlockTransaction, WalletTransaction> walletTransactionMap = new TreeMap<>();

        getWalletTransactions(wallet, walletTransactionMap, wallet.getNode(KeyPurpose.RECEIVE));
        getWalletTransactions(wallet, walletTransactionMap, wallet.getNode(KeyPurpose.CHANGE));

        return walletTransactionMap.values();
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
