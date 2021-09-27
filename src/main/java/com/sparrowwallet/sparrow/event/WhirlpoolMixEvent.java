package com.sparrowwallet.sparrow.event;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;

public class WhirlpoolMixEvent {
    private final Wallet wallet;
    private final BlockTransactionHashIndex utxo;
    private final MixProgress mixProgress;
    private final Utxo nextUtxo;
    private final MixFailReason mixFailReason;
    private final String mixError;

    public WhirlpoolMixEvent(Wallet wallet, BlockTransactionHashIndex utxo, MixProgress mixProgress) {
        this.wallet = wallet;
        this.utxo = utxo;
        this.mixProgress = mixProgress;
        this.nextUtxo = null;
        this.mixFailReason = null;
        this.mixError = null;
    }

    public WhirlpoolMixEvent(Wallet wallet, BlockTransactionHashIndex utxo, Utxo nextUtxo) {
        this.wallet = wallet;
        this.utxo = utxo;
        this.mixProgress = null;
        this.nextUtxo = nextUtxo;
        this.mixFailReason = null;
        this.mixError = null;
    }

    public WhirlpoolMixEvent(Wallet wallet, BlockTransactionHashIndex utxo, MixFailReason mixFailReason, String mixError) {
        this.wallet = wallet;
        this.utxo = utxo;
        this.mixProgress = null;
        this.nextUtxo = null;
        this.mixFailReason = mixFailReason;
        this.mixError = mixError;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public BlockTransactionHashIndex getUtxo() {
        return utxo;
    }

    public MixProgress getMixProgress() {
        return mixProgress;
    }

    public Utxo getNextUtxo() {
        return nextUtxo;
    }

    public MixFailReason getMixFailReason() {
        return mixFailReason;
    }

    public String getMixError() {
        return mixError;
    }
}
