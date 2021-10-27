package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.MixConfig;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MixConfigMapper implements RowMapper<MixConfig> {
    @Override
    public MixConfig map(ResultSet rs, StatementContext ctx) throws SQLException {
        String scode = rs.getString("scode");

        Boolean mixOnStartup = rs.getBoolean("mixOnStartup");
        if(rs.wasNull()) {
            mixOnStartup = null;
        }

        String indexRange = rs.getString("indexRange");
        String mixToWalletFile = rs.getString("mixToWalletFile");
        String mixToWalletName = rs.getString("mixToWalletName");

        Integer minMixes = rs.getInt("minMixes");
        if(rs.wasNull()) {
            minMixes = null;
        }

        MixConfig mixConfig = new MixConfig(scode, mixOnStartup, indexRange, mixToWalletFile == null ? null : new File(mixToWalletFile), mixToWalletName, minMixes, rs.getInt("receiveIndex"), rs.getInt("changeIndex"));
        mixConfig.setId(rs.getLong("id"));
        return mixConfig;
    }
}
