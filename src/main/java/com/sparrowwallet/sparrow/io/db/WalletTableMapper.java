package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.SortDirection;
import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.drongo.wallet.WalletTable;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

public class WalletTableMapper implements RowMapper<Map.Entry<TableType, WalletTable>> {
    @Override
    public Map.Entry<TableType, WalletTable> map(ResultSet rs, StatementContext ctx) throws SQLException {
        TableType tableType = TableType.values()[rs.getInt("type")];
        Object[] objWidths = (Object[])rs.getArray("widths").getArray();
        Double[] widths = Arrays.copyOf(objWidths, objWidths.length, Double[].class);
        int sortColumn = rs.getInt("sortColumn");
        SortDirection sortDirection = SortDirection.values()[rs.getInt("sortDirection")];

        WalletTable walletTable = new WalletTable(tableType, widths, sortColumn, sortDirection);
        walletTable.setId(rs.getLong("id"));

        return new Map.Entry<>() {
            @Override
            public TableType getKey() {
                return tableType;
            }

            @Override
            public WalletTable getValue() {
                return walletTable;
            }

            @Override
            public WalletTable setValue(WalletTable value) {
                return null;
            }
        };
    }
}
