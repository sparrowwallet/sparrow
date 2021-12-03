package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.CoinLabel;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.TransactionHashIndexEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NewWalletTransactionsEvent {
    private final Wallet wallet;
    private final List<TransactionEntry> transactionEntries;
    private final long totalBlockchainValue;
    private final long totalMempoolValue;

    public NewWalletTransactionsEvent(Wallet wallet, List<TransactionEntry> transactionEntries) {
        this.wallet = wallet;
        this.transactionEntries = transactionEntries;
        this.totalBlockchainValue = transactionEntries.stream().filter(txEntry -> txEntry.getConfirmations() > 0).mapToLong(Entry::getValue).sum();
        this.totalMempoolValue = transactionEntries.stream().filter(txEntry ->txEntry.getConfirmations() == 0).mapToLong(Entry::getValue).sum();
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<BlockTransaction> getBlockTransactions() {
        return transactionEntries.stream().map(TransactionEntry::getBlockTransaction).collect(Collectors.toList());
    }

    public long getTotalValue() {
        return totalBlockchainValue + totalMempoolValue;
    }

    public long getTotalBlockchainValue() {
        return totalBlockchainValue;
    }

    public long getTotalMempoolValue() {
        return totalMempoolValue;
    }

    public String getValueAsText(long value) {
        BitcoinUnit unit = Config.get().getBitcoinUnit();
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = (value >= BitcoinUnit.getAutoThreshold() ? BitcoinUnit.BTC : BitcoinUnit.SATOSHIS);
        }

        if(unit == BitcoinUnit.BTC) {
            return CoinLabel.getBTCFormat().format((double) value / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";
        }

        return String.format(Locale.ENGLISH, "%,d", value) + " sats";
    }

    public List<BlockTransaction> getUnspentConfirmingWhirlpoolMixTransactions() {
        List<BlockTransaction> mixTransactions = new ArrayList<>();
        if(wallet.isWhirlpoolMixWallet()) {
            return transactionEntries.stream()
                    .filter(txEntry -> txEntry.getValue() == 0 && txEntry.isConfirming() && !allOutputsSpent(txEntry))
                    .map(TransactionEntry::getBlockTransaction).collect(Collectors.toList());
        }

        return mixTransactions;
    }

    private boolean allOutputsSpent(TransactionEntry txEntry) {
        return txEntry.getChildren().stream()
                .map(refEntry -> ((TransactionHashIndexEntry)refEntry))
                .filter(txRefEntry -> txRefEntry.getType() == HashIndexEntry.Type.OUTPUT)
                .allMatch(txRefEntry -> txRefEntry.getHashIndex().isSpent());
    }
}
