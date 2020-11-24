package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class SendActionEvent {
    private final Wallet wallet;
    private final List<BlockTransactionHashIndex> utxos;

    public SendActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this.wallet = wallet;
        this.utxos = utxos;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }
}
