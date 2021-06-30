package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class WalletUtxoStatusChangedEvent extends WalletChangedEvent {
    private final List<BlockTransactionHashIndex> utxos;

    public WalletUtxoStatusChangedEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        super(wallet);
        this.utxos = utxos;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }
}
