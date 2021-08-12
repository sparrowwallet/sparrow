package com.sparrowwallet.sparrow.event;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class SpendUtxoEvent {
    private final Wallet wallet;
    private final List<BlockTransactionHashIndex> utxos;
    private final List<Payment> payments;
    private final Long fee;
    private final boolean includeSpentMempoolOutputs;
    private final Pool pool;

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this(wallet, utxos, null, null, false);
    }

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, List<Payment> payments, Long fee, boolean includeSpentMempoolOutputs) {
        this.wallet = wallet;
        this.utxos = utxos;
        this.payments = payments;
        this.fee = fee;
        this.includeSpentMempoolOutputs = includeSpentMempoolOutputs;
        this.pool = null;
    }

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, List<Payment> payments, Long fee, Pool pool) {
        this.wallet = wallet;
        this.utxos = utxos;
        this.payments = payments;
        this.fee = fee;
        this.includeSpentMempoolOutputs = false;
        this.pool = pool;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public Long getFee() {
        return fee;
    }

    public boolean isIncludeSpentMempoolOutputs() {
        return includeSpentMempoolOutputs;
    }

    public Pool getPool() {
        return pool;
    }
}
