package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.WalletTransaction;

public class ExcludeUtxoEvent {
    private final WalletTransaction walletTransaction;
    private final BlockTransactionHashIndex utxo;

    public ExcludeUtxoEvent(WalletTransaction walletTransaction, BlockTransactionHashIndex utxo) {
        this.walletTransaction = walletTransaction;
        this.utxo = utxo;
    }

    public WalletTransaction getWalletTransaction() {
        return walletTransaction;
    }

    public BlockTransactionHashIndex getUtxo() {
        return utxo;
    }
}
