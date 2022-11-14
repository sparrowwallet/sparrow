package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletConfig;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface WalletConfigDao {
    @SqlQuery("select id, iconData, userIcon, usePayNym from walletConfig where wallet = ?")
    @RegisterRowMapper(WalletConfigMapper.class)
    WalletConfig getForWalletId(Long id);

    @SqlUpdate("insert into walletConfig (iconData, userIcon, usePayNym, wallet) values (?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertWalletConfig(byte[] iconData, boolean userIcon, boolean usePayNym, long wallet);

    @SqlUpdate("update walletConfig set iconData = ?, userIcon = ?, usePayNym = ?, wallet = ? where id = ?")
    void updateWalletConfig(byte[] iconData, boolean userIcon, boolean usePayNym, long wallet, long id);

    default void addWalletConfig(Wallet wallet) {
        if(wallet.getWalletConfig() != null) {
            addOrUpdate(wallet, wallet.getWalletConfig());
        }
    }

    default void addOrUpdate(Wallet wallet, WalletConfig walletConfig) {
        if(walletConfig.getId() == null) {
            long id = insertWalletConfig(walletConfig.getIconData(), walletConfig.isUserIcon(), walletConfig.isUsePayNym(), wallet.getId());
            walletConfig.setId(id);
        } else {
            updateWalletConfig(walletConfig.getIconData(), walletConfig.isUserIcon(), walletConfig.isUsePayNym(), wallet.getId(), walletConfig.getId());
        }
    }
}
