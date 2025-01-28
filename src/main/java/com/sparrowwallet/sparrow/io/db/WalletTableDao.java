package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.TableType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletTable;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.HashMap;
import java.util.Map;

public interface WalletTableDao {
    @SqlQuery("select id, type, widths, sortColumn, sortDirection from walletTable where wallet = ?")
    @RegisterRowMapper(WalletTableMapper.class)
    Map<TableType, WalletTable> getForWalletId(Long id);

    @SqlQuery("select id, type, widths, sortColumn, sortDirection from walletTable where type = ?")
    @RegisterRowMapper(WalletTableMapper.class)
    Map<TableType, WalletTable> getForTypeId(int tableTypeId);

    @SqlUpdate("insert into walletTable (type, widths, sortColumn, sortDirection, wallet) values (?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertWalletTable(int tableType, Double[] widths, int sortColumn, int sortDirection, long wallet);

    @SqlUpdate("update walletTable set type = ?, widths = ?, sortColumn = ?, sortDirection = ?, wallet = ? where id = ?")
    void updateWalletTable(int tableType, Double[] widths, int sortColumn, int sortDirection, long wallet, long id);

    default void addWalletTables(Wallet wallet) {
        Map<TableType, WalletTable> walletTables = new HashMap<>(wallet.getWalletTables());
        for(Map.Entry<TableType, WalletTable> tableEntry : walletTables.entrySet()) {
            tableEntry.getValue().setId(null);
            addOrUpdate(wallet, tableEntry.getKey(), tableEntry.getValue());
        }
    }

    default void addOrUpdate(Wallet wallet, TableType tableType, WalletTable walletTable) {
        Map<TableType, WalletTable> existing = getForTypeId(tableType.ordinal());

        if(existing.isEmpty() && walletTable.getId() == null) {
            long id = insertWalletTable(walletTable.getTableType().ordinal(), walletTable.getWidths(),
                    walletTable.getSortColumn(), walletTable.getSortDirection().ordinal(), wallet.getId());
            walletTable.setId(id);
        } else {
            Long existingId = existing.get(tableType) != null ? existing.get(tableType).getId() : walletTable.getId();
            updateWalletTable(walletTable.getTableType().ordinal(), walletTable.getWidths(),
                    walletTable.getSortColumn(), walletTable.getSortDirection().ordinal(), wallet.getId(), existingId);
            walletTable.setId(existingId);
        }
    }
}
