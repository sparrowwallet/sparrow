package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.HashIndex;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NewWalletTransactionsEvent;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.property.LongProperty;
import javafx.beans.property.LongPropertyBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class WalletTransactionsEntry extends Entry {
    private static final Logger log = LoggerFactory.getLogger(WalletTransactionsEntry.class);

    public WalletTransactionsEntry(Wallet wallet) {
        super(wallet, wallet.getName(), getWalletTransactions(wallet).stream().map(WalletTransaction::getTransactionEntry).collect(Collectors.toList()));
        calculateBalances(false); //No need to resort
    }

    @Override
    public Long getValue() {
        return getBalance();
    }

    @Override
    public String getEntryType() {
        return "Wallet Transactions";
    }

    @Override
    public Function getWalletFunction() {
        return Function.TRANSACTIONS;
    }

    private void calculateBalances(boolean resort) {
        long balance = 0L;
        long mempoolBalance = 0L;

        if(resort) {
            //Note transaction entries must be in ascending order. This sorting is ultimately done according to BlockTransactions' comparator
            getChildren().sort(Comparator.comparing(TransactionEntry.class::cast));
        }

        for(Entry entry : getChildren()) {
            TransactionEntry transactionEntry = (TransactionEntry)entry;
            if(transactionEntry.getConfirmations() != 0 || transactionEntry.getValue() < 0 || Config.get().isIncludeMempoolOutputs()) {
                balance += entry.getValue();
            }

            if(transactionEntry.getConfirmations() == 0) {
                mempoolBalance += entry.getValue();
            }

            transactionEntry.setBalance(balance);
        }

        setBalance(balance);
        setMempoolBalance(mempoolBalance);
    }

    public void updateTransactions() {
        List<Entry> current = getWalletTransactions(getWallet()).stream().map(WalletTransaction::getTransactionEntry).collect(Collectors.toList());
        List<Entry> previous = new ArrayList<>(getChildren());

        List<Entry> entriesAdded = new ArrayList<>(current);
        entriesAdded.removeAll(previous);
        getChildren().addAll(entriesAdded);

        List<Entry> entriesRemoved = new ArrayList<>(previous);
        entriesRemoved.removeAll(current);
        getChildren().removeAll(entriesRemoved);

        calculateBalances(true);

        Map<HashIndex, BlockTransactionHashIndex> walletTxos = getWallet().getWalletTxos().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> new HashIndex(entry.getKey().getHash(), entry.getKey().getIndex()), Map.Entry::getKey));
        List<Entry> entriesComplete = entriesAdded.stream().filter(txEntry -> ((TransactionEntry)txEntry).isComplete(walletTxos)).collect(Collectors.toList());
        if(!entriesComplete.isEmpty()) {
            EventManager.get().post(new NewWalletTransactionsEvent(getWallet(), entriesAdded.stream().map(entry -> (TransactionEntry)entry).collect(Collectors.toList())));
        }

        if(entriesAdded.size() > entriesComplete.size()) {
            entriesAdded.removeAll(entriesComplete);
            for(Entry entry : entriesAdded) {
                TransactionEntry txEntry = (TransactionEntry)entry;
                getChildren().remove(txEntry);
                log.warn("Removing and not notifying incomplete entry " + ((TransactionEntry)entry).getBlockTransaction().getHashAsString() + " value " + txEntry.getValue());
            }
        }
    }

    private static Collection<WalletTransaction> getWalletTransactions(Wallet wallet) {
        Map<BlockTransaction, WalletTransaction> walletTransactionMap = new HashMap<>(wallet.getTransactions().size());

        getWalletTransactions(wallet, walletTransactionMap, wallet.getNode(KeyPurpose.RECEIVE));
        getWalletTransactions(wallet, walletTransactionMap, wallet.getNode(KeyPurpose.CHANGE));

        List<WalletTransaction> walletTransactions = new ArrayList<>(walletTransactionMap.values());
        Collections.sort(walletTransactions);
        return walletTransactions;
    }

    private static void getWalletTransactions(Wallet wallet, Map<BlockTransaction, WalletTransaction> walletTransactionMap, WalletNode purposeNode) {
        KeyPurpose keyPurpose = purposeNode.getKeyPurpose();
        List<WalletNode> childNodes = new ArrayList<>(purposeNode.getChildren());
        for(WalletNode addressNode : childNodes) {
            for(BlockTransactionHashIndex hashIndex : addressNode.getTransactionOutputs()) {
                BlockTransaction inputTx = wallet.getTransactions().get(hashIndex.getHash());
                //A null inputTx here means the wallet is still updating - ignore as the WalletHistoryChangedEvent will run this again
                if(inputTx != null) {
                    WalletTransaction inputWalletTx = walletTransactionMap.get(inputTx);
                    if(inputWalletTx == null) {
                        inputWalletTx = new WalletTransaction(wallet, inputTx);
                        walletTransactionMap.put(inputTx, inputWalletTx);
                    }
                    inputWalletTx.incoming.put(hashIndex, keyPurpose);

                    if(hashIndex.getSpentBy() != null) {
                        BlockTransaction outputTx = wallet.getTransactions().get(hashIndex.getSpentBy().getHash());
                        if(outputTx != null) {
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

    /**
     * Defines the wallet balance in the mempool
     */
    private LongProperty mempoolBalance;

    public final void setMempoolBalance(long value) {
        if(mempoolBalance != null || value != 0) {
            mempoolBalanceProperty().set(value);
        }
    }

    public final long getMempoolBalance() {
        return mempoolBalance == null ? 0L : mempoolBalance.get();
    }

    public final LongProperty mempoolBalanceProperty() {
        if(mempoolBalance == null) {
            mempoolBalance = new LongPropertyBase(0L) {

                @Override
                public Object getBean() {
                    return WalletTransactionsEntry.this;
                }

                @Override
                public String getName() {
                    return "mempoolBalance";
                }
            };
        }
        return mempoolBalance;
    }

    private static class WalletTransaction implements Comparable<WalletTransaction> {
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

        public long getValue() {
            long value = 0L;
            for(BlockTransactionHashIndex in : incoming.keySet()) {
                value += in.getValue();
            }
            for(BlockTransactionHashIndex out : outgoing.keySet()) {
                value -= out.getValue();
            }

            return value;
        }

        @Override
        public int compareTo(WalletTransactionsEntry.WalletTransaction other) {
            //This comparison must be identical to that of TransactionEntry so we can avoid a resort calculating balances when creating WalletTransactionsEntry
            int blockOrder = blockTransaction.compareBlockOrder(other.blockTransaction);
            if(blockOrder != 0) {
                return blockOrder;
            }

            int valueOrder = Long.compare(other.getValue(), getValue());
            if(valueOrder != 0) {
                return valueOrder;
            }

            return blockTransaction.compareTo(other.blockTransaction);
        }
    }
}
