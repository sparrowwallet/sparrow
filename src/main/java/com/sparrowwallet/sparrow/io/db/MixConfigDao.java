package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface MixConfigDao {
    @SqlQuery("select id, scode, mixOnStartup, indexRange, mixToWalletFile, mixToWalletName, minMixes, receiveIndex, changeIndex from mixConfig where wallet = ?")
    @RegisterRowMapper(MixConfigMapper.class)
    MixConfig getForWalletId(Long id);

    @SqlUpdate("insert into mixConfig (scode, mixOnStartup, indexRange, mixToWalletFile, mixToWalletName, minMixes, receiveIndex, changeIndex, wallet) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertMixConfig(String scode, Boolean mixOnStartup, String indexRange, String mixToWalletFile, String mixToWalletName, Integer minMixes, int receiveIndex, int changeIndex, long wallet);

    @SqlUpdate("update mixConfig set scode = ?, mixOnStartup = ?, indexRange = ?, mixToWalletFile = ?, mixToWalletName = ?, minMixes = ?, receiveIndex = ?, changeIndex = ?, wallet = ? where id = ?")
    void updateMixConfig(String scode, Boolean mixOnStartup, String indexRange, String mixToWalletFile, String mixToWalletName, Integer minMixes, int receiveIndex, int changeIndex, long wallet, long id);

    default void addMixConfig(Wallet wallet) {
        if(wallet.getMixConfig() != null) {
            wallet.getMixConfig().setId(null);
            addOrUpdate(wallet, wallet.getMixConfig());
        }
    }

    default void addOrUpdate(Wallet wallet, MixConfig mixConfig) {
        String mixToWalletFile = null;
        if(mixConfig.getMixToWalletFile() != null) {
            mixToWalletFile = mixConfig.getMixToWalletFile().getAbsolutePath();
        }

        if(mixConfig.getId() == null) {
            long id = insertMixConfig(mixConfig.getScode(), mixConfig.getMixOnStartup(), mixConfig.getIndexRange(), mixToWalletFile, mixConfig.getMixToWalletName(), mixConfig.getMinMixes(), mixConfig.getReceiveIndex(), mixConfig.getChangeIndex(), wallet.getId());
            mixConfig.setId(id);
        } else {
            updateMixConfig(mixConfig.getScode(), mixConfig.getMixOnStartup(), mixConfig.getIndexRange(), mixToWalletFile, mixConfig.getMixToWalletName(), mixConfig.getMinMixes(), mixConfig.getReceiveIndex(), mixConfig.getChangeIndex(), wallet.getId(), mixConfig.getId());
        }
    }
}
