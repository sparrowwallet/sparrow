package com.sparrowwallet.sparrow.event;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class SpendUtxoEvent {
    private final Wallet wallet;
    private final List<BlockTransactionHashIndex> utxos;
    private final List<Payment> payments;
    private final List<byte[]> opReturns;
    private final Long fee;
    private final boolean includeSpentMempoolOutputs;
    private final Pool pool;
    private final PaymentCode paymentCode;

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this(wallet, utxos, null, null, null, false);
    }

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, List<Payment> payments, List<byte[]> opReturns, Long fee, boolean includeSpentMempoolOutputs) {
        this.wallet = wallet;
        this.utxos = utxos;
        this.payments = payments;
        this.opReturns = opReturns;
        this.fee = fee;
        this.includeSpentMempoolOutputs = includeSpentMempoolOutputs;
        this.pool = null;
        this.paymentCode = null;
    }

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, List<Payment> payments, List<byte[]> opReturns, Long fee, Pool pool) {
        this.wallet = wallet;
        this.utxos = utxos;
        this.payments = payments;
        this.opReturns = opReturns;
        this.fee = fee;
        this.includeSpentMempoolOutputs = false;
        this.pool = pool;
        this.paymentCode = null;
    }

    public SpendUtxoEvent(Wallet wallet, List<Payment> payments, List<byte[]> opReturns, PaymentCode paymentCode) {
        this.wallet = wallet;
        this.utxos = null;
        this.payments = payments;
        this.opReturns = opReturns;
        this.fee = null;
        this.includeSpentMempoolOutputs = false;
        this.pool = null;
        this.paymentCode = paymentCode;
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

    public List<byte[]> getOpReturns() {
        return opReturns;
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

    public PaymentCode getPaymentCode() {
        return paymentCode;
    }
}
