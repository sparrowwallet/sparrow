package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletUtxoStatusChangedEvent extends WalletDataChangedEvent {
    private final BlockTransactionHashIndex utxo;

    public WalletUtxoStatusChangedEvent(Wallet wallet, BlockTransactionHashIndex utxo) {
        super(wallet);
        this.utxo = utxo;
    }

    public BlockTransactionHashIndex getUtxo() {
        return utxo;
    }
}
