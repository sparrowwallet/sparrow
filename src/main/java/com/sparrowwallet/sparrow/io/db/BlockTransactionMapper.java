package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class BlockTransactionMapper implements RowMapper<Map.Entry<Sha256Hash, BlockTransaction>> {

    @Override
    public Map.Entry<Sha256Hash, BlockTransaction> map(ResultSet rs, StatementContext ctx) throws SQLException {
        Sha256Hash txid = Sha256Hash.wrap(rs.getBytes("txid"));

        byte[] txBytes = rs.getBytes("transaction");
        Transaction transaction = null;
        if(txBytes != null) {
            transaction = new Transaction(txBytes);
        }

        Long fee = rs.getLong("fee");
        if(rs.wasNull()) {
            fee = null;
        }

        BlockTransaction blockTransaction = new BlockTransaction(Sha256Hash.wrap(rs.getBytes("hash")), rs.getInt("height"), rs.getTimestamp("date"),
                fee, transaction, rs.getBytes("blockHash") == null ? null : Sha256Hash.wrap(rs.getBytes("blockHash")), rs.getString("label"));
        blockTransaction.setId(rs.getLong("id"));

        return new Map.Entry<>() {
            @Override
            public Sha256Hash getKey() {
                return txid;
            }

            @Override
            public BlockTransaction getValue() {
                return blockTransaction;
            }

            @Override
            public BlockTransaction setValue(BlockTransaction value) {
                return null;
            }
        };
    }
}
