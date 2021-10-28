package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public interface BlockTransactionDao {
    @SqlQuery("select id, txid, hash, height, date, fee, label, transaction, blockHash from blockTransaction where wallet = ? order by id")
    @RegisterRowMapper(BlockTransactionMapper.class)
    Map<Sha256Hash, BlockTransaction> getForWalletId(Long id);

    @SqlQuery("select id, txid, hash, height, date, fee, label, transaction, blockHash from blockTransaction where txid = ?")
    @RegisterRowMapper(BlockTransactionMapper.class)
    Map<Sha256Hash, BlockTransaction> getForTxId(byte[] id);

    @SqlUpdate("insert into blockTransaction (txid, hash, height, date, fee, label, transaction, blockHash, wallet) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertBlockTransaction(byte[] txid, byte[] hash, int height, Date date, Long fee, String label, byte[] transaction, byte[] blockHash, long wallet);

    @SqlUpdate("update blockTransaction set txid = ?, hash = ?, height = ?, date = ?, fee = ?, label = ?, transaction = ?, blockHash = ?, wallet = ? where id = ?")
    void updateBlockTransaction(byte[] txid, byte[] hash, int height, Date date, Long fee, String label, byte[] transaction, byte[] blockHash, long wallet, long id);

    @SqlUpdate("update blockTransaction set label = :label where id = :id")
    void updateLabel(@Bind("id") long id, @Bind("label") String label);

    @SqlUpdate("delete from blockTransaction where wallet = ?")
    void clear(long wallet);

    default void addBlockTransactions(Wallet wallet) {
        Map<Sha256Hash, BlockTransaction> walletTransactions = new HashMap<>(wallet.getTransactions());
        for(Map.Entry<Sha256Hash, BlockTransaction> blkTxEntry : walletTransactions.entrySet()) {
            blkTxEntry.getValue().setId(null);
            addOrUpdate(wallet, blkTxEntry.getKey(), blkTxEntry.getValue());
        }
    }

    default void addOrUpdate(Wallet wallet, Sha256Hash txid, BlockTransaction blkTx) {
        Map<Sha256Hash, BlockTransaction> existing = getForTxId(txid.getBytes());

        if(existing.isEmpty() && blkTx.getId() == null) {
            long id = insertBlockTransaction(txid.getBytes(), blkTx.getHash().getBytes(), blkTx.getHeight(), blkTx.getDate(), blkTx.getFee(), truncate(blkTx.getLabel()),
                    blkTx.getTransaction() == null ? null : blkTx.getTransaction().bitcoinSerialize(),
                    blkTx.getBlockHash() == null ? null : blkTx.getBlockHash().getBytes(), wallet.getId());
            blkTx.setId(id);
        } else {
            Long existingId = existing.get(txid) != null ? existing.get(txid).getId() : blkTx.getId();
            updateBlockTransaction(txid.getBytes(), blkTx.getHash().getBytes(), blkTx.getHeight(), blkTx.getDate(), blkTx.getFee(), truncate(blkTx.getLabel()),
                    blkTx.getTransaction() == null ? null : blkTx.getTransaction().bitcoinSerialize(),
                    blkTx.getBlockHash() == null ? null : blkTx.getBlockHash().getBytes(), wallet.getId(), existingId);
            blkTx.setId(existingId);
        }
    }

    default String truncate(String label) {
        return (label != null && label.length() > 255 ? label.substring(0, 255) : label);
    }
}
