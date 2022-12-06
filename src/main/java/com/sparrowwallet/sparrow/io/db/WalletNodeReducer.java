package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.WalletNode;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;

import java.util.Map;

public class WalletNodeReducer implements LinkedHashMapRowReducer<Long, WalletNode> {
    private static final BlockTransactionHashIndex INPUT_MARKER = new BlockTransactionHashIndex(Sha256Hash.ZERO_HASH, 0, null, null, 0, 0);

    @Override
    public void accumulate(Map<Long, WalletNode> map, RowView rowView) {
        WalletNode walletNode = map.computeIfAbsent(rowView.getColumn("walletNode.id", Long.class), id -> rowView.getRow(WalletNode.class));

        if(rowView.getColumn("walletNode.parent", Long.class) != null) {
            WalletNode parentNode = map.get(rowView.getColumn("walletNode.parent", Long.class));
            parentNode.getChildren().add(walletNode);
        }

        if(rowView.getColumn("blockTransactionHashIndex.node", Long.class) != null) {
            BlockTransactionHashIndex blockTransactionHashIndex = rowView.getRow(BlockTransactionHashIndex.class);
            if(rowView.getColumn("blockTransactionHashIndex.spentBy", Long.class) != null) {
                BlockTransactionHashIndex spentBy = walletNode.getTransactionOutputs().stream().filter(ref -> ref.getId().equals(rowView.getColumn("blockTransactionHashIndex.spentBy", Long.class))).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Cannot find transaction output for " + rowView.getColumn("blockTransactionHashIndex.spentBy", Long.class)));
                blockTransactionHashIndex.setSpentBy(spentBy);
                walletNode.getTransactionOutputs().remove(spentBy);
                spentBy.setSpentBy(null);
            }
            if(!walletNode.getTransactionOutputs().add(blockTransactionHashIndex)) {
                //Can only happen if we add an input with the same hash, index, height etc as an existing output
                //Set a marker we will clear when adding the output
                blockTransactionHashIndex.setSpentBy(INPUT_MARKER);
                walletNode.getTransactionOutputs().add(blockTransactionHashIndex);
            }
        }
    }
}
