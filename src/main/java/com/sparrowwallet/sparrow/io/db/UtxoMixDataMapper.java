package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.UtxoMixData;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class UtxoMixDataMapper implements RowMapper<Map.Entry<Sha256Hash, UtxoMixData>> {
    @Override
    public Map.Entry<Sha256Hash, UtxoMixData> map(ResultSet rs, StatementContext ctx) throws SQLException {
        Sha256Hash hash = Sha256Hash.wrap(rs.getBytes("hash"));

        Long forwarding = rs.getLong("forwarding");
        if(rs.wasNull()) {
            forwarding = null;
        }

        UtxoMixData utxoMixData = new UtxoMixData(rs.getString("poolId"), rs.getInt("mixesDone"), forwarding);
        utxoMixData.setId(rs.getLong("id"));

        return new Map.Entry<>() {
            @Override
            public Sha256Hash getKey() {
                return hash;
            }

            @Override
            public UtxoMixData getValue() {
                return utxoMixData;
            }

            @Override
            public UtxoMixData setValue(UtxoMixData value) {
                return null;
            }
        };
    }
}
