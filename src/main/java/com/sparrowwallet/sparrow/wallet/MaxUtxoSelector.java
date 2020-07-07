package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.UtxoSelector;

import java.util.Collection;
import java.util.Collections;

public class MaxUtxoSelector implements UtxoSelector {
    @Override
    public Collection<BlockTransactionHashIndex> select(long targetValue, Collection<BlockTransactionHashIndex> candidates) {
        return Collections.unmodifiableCollection(candidates);
    }
}
