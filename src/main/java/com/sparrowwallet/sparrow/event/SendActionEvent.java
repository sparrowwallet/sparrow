package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Function;

import java.util.List;

public class SendActionEvent extends FunctionActionEvent {
    private final List<BlockTransactionHashIndex> utxos;

    public SendActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        super(Function.SEND, wallet);
        this.utxos = utxos;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }

    @Override
    public boolean selectFunction() {
        return !getUtxos().isEmpty();
    }
}
