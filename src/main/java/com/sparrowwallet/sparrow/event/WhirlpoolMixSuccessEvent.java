package com.sparrowwallet.sparrow.event;

import com.samourai.whirlpool.protocol.beans.Utxo;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

public class WhirlpoolMixSuccessEvent extends WhirlpoolMixEvent {
    private final WalletNode walletNode;

    public WhirlpoolMixSuccessEvent(Wallet wallet, BlockTransactionHashIndex utxo, Utxo nextUtxo, WalletNode walletNode) {
        super(wallet, utxo, nextUtxo);
        this.walletNode = walletNode;
    }

    public WalletNode getWalletNode() {
        return walletNode;
    }
}
