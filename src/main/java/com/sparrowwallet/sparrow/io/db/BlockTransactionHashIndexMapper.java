package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Status;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BlockTransactionHashIndexMapper implements RowMapper<BlockTransactionHashIndex> {
    @Override
    public BlockTransactionHashIndex map(ResultSet rs, StatementContext ctx) throws SQLException {
        BlockTransactionHashIndex blockTransactionHashIndex = new BlockTransactionHashIndex(Sha256Hash.wrap(rs.getBytes("blockTransactionHashIndex.hash")),
                rs.getInt("blockTransactionHashIndex.height"), rs.getTimestamp("blockTransactionHashIndex.date"), rs.getLong("blockTransactionHashIndex.fee"),
                rs.getLong("blockTransactionHashIndex.index"), rs.getLong("blockTransactionHashIndex.outputValue"), null, rs.getString("blockTransactionHashIndex.label"));
        blockTransactionHashIndex.setId(rs.getLong("blockTransactionHashIndex.id"));
        int statusIndex = rs.getInt("blockTransactionHashIndex.status");
        if(!rs.wasNull()) {
            blockTransactionHashIndex.setStatus(Status.values()[statusIndex]);
        }

        return blockTransactionHashIndex;
    }
}
