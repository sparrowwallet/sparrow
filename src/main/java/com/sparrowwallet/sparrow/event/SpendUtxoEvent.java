package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class SpendUtxoEvent {
    private final Wallet wallet;
    private final List<BlockTransactionHashIndex> utxos;
    private final List<Payment> payments;
    private final Long fee;
    private final boolean includeMempoolInputs;

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this(wallet, utxos, null, null, false);
    }

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, List<Payment> payments, Long fee, boolean includeMempoolInputs) {
        this.wallet = wallet;
        this.utxos = utxos;
        this.payments = payments;
        this.fee = fee;
        this.includeMempoolInputs = includeMempoolInputs;
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

    public boolean isIncludeMempoolInputs() {
        return includeMempoolInputs;
    }
}
