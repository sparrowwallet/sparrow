package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public interface WalletNodeDao {
    @SqlQuery("select walletNode.id, walletNode.derivationPath, walletNode.label, walletNode.parent, " +
            "blockTransactionHashIndex.id, blockTransactionHashIndex.hash, blockTransactionHashIndex.height, blockTransactionHashIndex.date, blockTransactionHashIndex.fee, blockTransactionHashIndex.label, " +
            "blockTransactionHashIndex.index, blockTransactionHashIndex.outputValue, blockTransactionHashIndex.status, blockTransactionHashIndex.spentBy, blockTransactionHashIndex.node " +
            "from walletNode left join blockTransactionHashIndex on walletNode.id = blockTransactionHashIndex.node where walletNode.wallet = ? order by walletNode.parent asc nulls first, blockTransactionHashIndex.spentBy asc nulls first")
    @RegisterRowMapper(WalletNodeMapper.class)
    @RegisterRowMapper(BlockTransactionHashIndexMapper.class)
    @UseRowReducer(WalletNodeReducer.class)
    List<WalletNode> getForWalletId(Long id);

    @SqlUpdate("insert into walletNode (derivationPath, label, wallet, parent) values (?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertWalletNode(String derivationPath, String label, long wallet, Long parent);

    @SqlUpdate("insert into blockTransactionHashIndex (hash, height, date, fee, label, index, outputValue, status, spentBy, node) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertBlockTransactionHashIndex(byte[] hash, int height, Date date, Long fee, String label, long index, long value, Integer status, Long spentBy, long node);

    @SqlUpdate("update blockTransactionHashIndex set hash = ?, height = ?, date = ?, fee = ?, label = ?, index = ?, outputValue = ?, status = ?, spentBy = ?, node = ? where id = ?")
    void updateBlockTransactionHashIndex(byte[] hash, int height, Date date, Long fee, String label, long index, long value, Integer status, Long spentBy, long node, long id);

    @SqlUpdate("update walletNode set label = :label where id = :id")
    void updateNodeLabel(@Bind("id") long id, @Bind("label") String label);

    @SqlUpdate("update blockTransactionHashIndex set label = :label where id = :id")
    void updateTxoLabel(@Bind("id") long id, @Bind("label") String label);

    @SqlUpdate("update blockTransactionHashIndex set status = :status where id = :id")
    void updateTxoStatus(@Bind("id") long id, @Bind("status") Integer status);

    @SqlUpdate("delete from blockTransactionHashIndex where blockTransactionHashIndex.node in (select walletNode.id from walletNode where walletNode.wallet = ?)")
    void clearHistory(long wallet);

    @SqlUpdate("delete from blockTransactionHashIndex where blockTransactionHashIndex.node in (select walletNode.id from walletNode where walletNode.wallet = ?) and blockTransactionHashIndex.spentBy is not null")
    void clearSpentHistory(long wallet);

    @SqlUpdate("delete from blockTransactionHashIndex where node = :nodeId and id not in (<ids>)")
    void deleteUnreferencedNodeTxos(@Bind("nodeId") Long nodeId, @BindList("ids") List<Long> ids);

    @SqlUpdate("delete from blockTransactionHashIndex where node = :nodeId and id not in (<ids>) and spentBy is not null")
    void deleteUnreferencedNodeSpentTxos(@Bind("nodeId") Long nodeId, @BindList("ids") List<Long> ids);

    default void addWalletNodes(Wallet wallet) {
        for(WalletNode purposeNode : wallet.getPurposeNodes()) {
            long purposeNodeId = insertWalletNode(purposeNode.getDerivationPath(), truncate(purposeNode.getLabel()), wallet.getId(), null);
            purposeNode.setId(purposeNodeId);
            List<WalletNode> childNodes = new ArrayList<>(purposeNode.getChildren());
            for(WalletNode addressNode : childNodes) {
                long addressNodeId = insertWalletNode(addressNode.getDerivationPath(), truncate(addressNode.getLabel()), wallet.getId(), purposeNodeId);
                addressNode.setId(addressNodeId);
                addTransactionOutputs(addressNode);
            }
        }
    }

    default void addTransactionOutputs(WalletNode addressNode) {
        for(BlockTransactionHashIndex txo : addressNode.getTransactionOutputs()) {
            txo.setId(null);
            if(txo.isSpent()) {
                txo.getSpentBy().setId(null);
            }

            addOrUpdate(addressNode, txo);
        }
    }

    default void addOrUpdate(WalletNode addressNode, BlockTransactionHashIndex txo) {
        Long spentById = null;
        if(txo.isSpent()) {
            BlockTransactionHashIndex spentBy = txo.getSpentBy();
            if(spentBy.getId() == null) {
                spentById = insertBlockTransactionHashIndex(spentBy.getHash().getBytes(), spentBy.getHeight(), spentBy.getDate(), spentBy.getFee(), truncate(spentBy.getLabel()), spentBy.getIndex(), spentBy.getValue(),
                        spentBy.getStatus() == null ? null : spentBy.getStatus().ordinal(), null, addressNode.getId());
                spentBy.setId(spentById);
            } else {
                updateBlockTransactionHashIndex(spentBy.getHash().getBytes(), spentBy.getHeight(), spentBy.getDate(), spentBy.getFee(), truncate(spentBy.getLabel()), spentBy.getIndex(), spentBy.getValue(),
                        spentBy.getStatus() == null ? null : spentBy.getStatus().ordinal(), null, addressNode.getId(), spentBy.getId());
                spentById = spentBy.getId();
            }
        }

        if(txo.getId() == null) {
            long txoId = insertBlockTransactionHashIndex(txo.getHash().getBytes(), txo.getHeight(), txo.getDate(), txo.getFee(), truncate(txo.getLabel()), txo.getIndex(), txo.getValue(),
                    txo.getStatus() == null ? null : txo.getStatus().ordinal(), spentById, addressNode.getId());
            txo.setId(txoId);
        } else {
            updateBlockTransactionHashIndex(txo.getHash().getBytes(), txo.getHeight(), txo.getDate(), txo.getFee(), truncate(txo.getLabel()), txo.getIndex(), txo.getValue(),
                    txo.getStatus() == null ? null : txo.getStatus().ordinal(), spentById, addressNode.getId(), txo.getId());
        }
    }

    default void deleteNodeTxosNotInList(WalletNode addressNode, List<Long> txoIds) {
        deleteUnreferencedNodeSpentTxos(addressNode.getId(), txoIds);
        deleteUnreferencedNodeTxos(addressNode.getId(), txoIds);
    }

    default void clearHistory(Wallet wallet) {
        clearSpentHistory(wallet.getId());
        clearHistory(wallet.getId());
    }

    default String truncate(String label) {
        return (label != null && label.length() > 255 ? label.substring(0, 255) : label);
    }
}
