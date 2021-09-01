package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface MixConfigDao {
    @SqlQuery("select id, scode, mixOnStartup, mixToWalletFile, mixToWalletName, minMixes from mixConfig where wallet = ?")
    @RegisterRowMapper(MixConfigMapper.class)
    MixConfig getForWalletId(Long id);

    @SqlUpdate("insert into mixConfig (scode, mixOnStartup, mixToWalletFile, mixToWalletName, minMixes, wallet) values (?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertMixConfig(String scode, Boolean mixOnStartup, String mixToWalletFile, String mixToWalletName, Integer minMixes, long wallet);

    @SqlUpdate("update mixConfig set scode = ?, mixOnStartup = ?, mixToWalletFile = ?, mixToWalletName = ?, minMixes = ?, wallet = ? where id = ?")
    void updateMixConfig(String scode, Boolean mixOnStartup, String mixToWalletFile, String mixToWalletName, Integer minMixes, long wallet, long id);

    default void addMixConfig(Wallet wallet) {
        if(wallet.getMixConfig() != null) {
            addOrUpdate(wallet, wallet.getMixConfig());
        }
    }

    default void addOrUpdate(Wallet wallet, MixConfig mixConfig) {
        String mixToWalletFile = null;
        if(mixConfig.getMixToWalletFile() != null) {
            mixToWalletFile = mixConfig.getMixToWalletFile().getAbsolutePath();
        }

        if(mixConfig.getId() == null) {
            long id = insertMixConfig(mixConfig.getScode(), mixConfig.getMixOnStartup(), mixToWalletFile, mixConfig.getMixToWalletName(), mixConfig.getMinMixes(), wallet.getId());
            mixConfig.setId(id);
        } else {
            updateMixConfig(mixConfig.getScode(), mixConfig.getMixOnStartup(), mixToWalletFile, mixConfig.getMixToWalletName(), mixConfig.getMinMixes(), wallet.getId(), mixConfig.getId());
        }
    }
}
