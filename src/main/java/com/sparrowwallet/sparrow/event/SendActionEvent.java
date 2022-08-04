package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Function;

import java.util.List;

public class SendActionEvent extends FunctionActionEvent {
    private final List<BlockTransactionHashIndex> utxos;
    private final boolean selectIfEmpty;

    public SendActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this(wallet, utxos, false);
    }

    public SendActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, boolean selectIfEmpty) {
        super(Function.SEND, wallet);
        this.utxos = utxos;
        this.selectIfEmpty = selectIfEmpty;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }

    @Override
    public boolean selectFunction() {
        return selectIfEmpty || !getUtxos().isEmpty();
    }
}
