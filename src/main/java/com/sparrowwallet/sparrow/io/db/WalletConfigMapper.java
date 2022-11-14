package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.WalletConfig;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WalletConfigMapper  implements RowMapper<WalletConfig> {
    @Override
    public WalletConfig map(ResultSet rs, StatementContext ctx) throws SQLException {
        byte[] iconData = rs.getBytes("iconData");
        boolean userIcon = rs.getBoolean("userIcon");
        boolean usePayNym = rs.getBoolean("usePayNym");

        WalletConfig walletConfig = new WalletConfig(iconData, userIcon, usePayNym);
        walletConfig.setId(rs.getLong("id"));
        return walletConfig;
    }
}
