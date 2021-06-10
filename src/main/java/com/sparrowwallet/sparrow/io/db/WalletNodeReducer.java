package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.WalletNode;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;

import java.util.Map;

public class WalletNodeReducer implements LinkedHashMapRowReducer<Long, WalletNode> {
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
                BlockTransactionHashIndex spentBy = walletNode.getTransactionOutputs().stream().filter(ref -> ref.getId().equals(rowView.getColumn("blockTransactionHashIndex.spentBy", Long.class))).findFirst().orElseThrow();
                blockTransactionHashIndex.setSpentBy(spentBy);
                walletNode.getTransactionOutputs().remove(spentBy);
            }
            walletNode.getTransactionOutputs().add(blockTransactionHashIndex);
        }
    }
}
