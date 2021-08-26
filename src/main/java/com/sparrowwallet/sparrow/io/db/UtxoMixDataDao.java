package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.UtxoMixData;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Map;

public interface UtxoMixDataDao {
    @SqlQuery("select id, hash, mixesDone, expired from utxoMixData where wallet = ? order by id")
    @RegisterRowMapper(UtxoMixDataMapper.class)
    Map<Sha256Hash, UtxoMixData> getForWalletId(Long id);

    @SqlQuery("select id, hash, mixesDone, expired from utxoMixData where hash = ?")
    @RegisterRowMapper(UtxoMixDataMapper.class)
    Map<Sha256Hash, UtxoMixData> getForHash(byte[] hash);

    @SqlUpdate("insert into utxoMixData (hash, mixesDone, expired, wallet) values (?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertUtxoMixData(byte[] hash, int mixesDone, Long expired, long wallet);

    @SqlUpdate("update utxoMixData set hash = ?, mixesDone = ?, expired = ?, wallet = ? where id = ?")
    void updateUtxoMixData(byte[] hash, int mixesDone, Long expired, long wallet, long id);

    @SqlUpdate("delete from utxoMixData where id in (<ids>)")
    void deleteUtxoMixData(@BindList("ids") List<Long> ids);

    @SqlUpdate("delete from utxoMixData where wallet = ?")
    void clear(long wallet);

    default void addUtxoMixData(Wallet wallet) {
        for(Map.Entry<Sha256Hash, UtxoMixData> utxoMixDataEntry : wallet.getUtxoMixes().entrySet()) {
            utxoMixDataEntry.getValue().setId(null);
            addOrUpdate(wallet, utxoMixDataEntry.getKey(), utxoMixDataEntry.getValue());
        }
    }

    default void addOrUpdate(Wallet wallet, Sha256Hash hash, UtxoMixData utxoMixData) {
        Map<Sha256Hash, UtxoMixData> existing = getForHash(hash.getBytes());

        if(existing.isEmpty() && utxoMixData.getId() == null) {
            long id = insertUtxoMixData(hash.getBytes(), utxoMixData.getMixesDone(), utxoMixData.getExpired(), wallet.getId());
            utxoMixData.setId(id);
        } else {
            Long existingId = existing.get(hash) != null ? existing.get(hash).getId() : utxoMixData.getId();
            updateUtxoMixData(hash.getBytes(), utxoMixData.getMixesDone(), utxoMixData.getExpired(), wallet.getId(), existingId);
            utxoMixData.setId(existingId);
        }
    }
}
