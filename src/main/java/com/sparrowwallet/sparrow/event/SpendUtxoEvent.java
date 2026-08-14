package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
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
    private final boolean requireAllUtxos;
    private final BlockTransaction replacedTransaction;
    private final PaymentCode paymentCode;
    private final boolean allowPaymentChanges;

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this(wallet, utxos, null, null, null, false, null, true);
    }

    public SpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, List<Payment> payments, List<byte[]> opReturns, Long fee, boolean requireAllUtxos, BlockTransaction replacedTransaction, boolean allowPaymentChanges) {
        this.wallet = wallet;
        this.utxos = utxos;
        this.payments = payments;
        this.opReturns = opReturns;
        this.fee = fee;
        this.requireAllUtxos = requireAllUtxos;
        this.replacedTransaction = replacedTransaction;
        this.paymentCode = null;
        this.allowPaymentChanges = allowPaymentChanges;
    }

    public SpendUtxoEvent(Wallet wallet, List<Payment> payments, List<byte[]> opReturns, PaymentCode paymentCode) {
        this.wallet = wallet;
        this.utxos = null;
        this.payments = payments;
        this.opReturns = opReturns;
        this.fee = null;
        this.requireAllUtxos = false;
        this.replacedTransaction = null;
        this.paymentCode = paymentCode;
        this.allowPaymentChanges = false;
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

    public boolean isRequireAllUtxos() {
        return requireAllUtxos;
    }

    public BlockTransaction getReplacedTransaction() {
        return replacedTransaction;
    }

    public PaymentCode getPaymentCode() {
        return paymentCode;
    }

    public boolean allowPaymentChanges() {
        return allowPaymentChanges;
    }
}
