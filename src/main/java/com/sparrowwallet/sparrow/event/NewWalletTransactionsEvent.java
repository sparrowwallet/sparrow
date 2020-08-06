package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class NewWalletTransactionsEvent {
    private final Wallet wallet;
    private final List<BlockTransaction> blockTransactions;
    private final long totalValue;

    public NewWalletTransactionsEvent(Wallet wallet, List<BlockTransaction> blockTransactions, long totalValue) {
        this.wallet = wallet;
        this.blockTransactions = blockTransactions;
        this.totalValue = totalValue;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<BlockTransaction> getBlockTransactions() {
        return blockTransactions;
    }

    public long getTotalValue() {
        return totalValue;
    }
}
