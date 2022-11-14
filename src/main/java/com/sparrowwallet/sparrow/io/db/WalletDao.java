package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.*;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface WalletDao {
    @CreateSqlObject
    PolicyDao createPolicyDao();

    @CreateSqlObject
    KeystoreDao createKeystoreDao();

    @CreateSqlObject
    WalletNodeDao createWalletNodeDao();

    @CreateSqlObject
    BlockTransactionDao createBlockTransactionDao();

    @CreateSqlObject
    DetachedLabelDao createDetachedLabelDao();

    @CreateSqlObject
    WalletConfigDao createWalletConfigDao();

    @CreateSqlObject
    MixConfigDao createMixConfigDao();

    @CreateSqlObject
    UtxoMixDataDao createUtxoMixDataDao();

    @SqlQuery("select wallet.id, wallet.name, wallet.label, wallet.network, wallet.policyType, wallet.scriptType, wallet.storedBlockHeight, wallet.gapLimit, wallet.watchLast, wallet.birthDate, policy.id, policy.name, policy.script from wallet left join policy on wallet.defaultPolicy = policy.id")
    @RegisterRowMapper(WalletMapper.class)
    List<Wallet> loadAllWallets();

    @SqlQuery("select wallet.id, wallet.name, wallet.label, wallet.network, wallet.policyType, wallet.scriptType, wallet.storedBlockHeight, wallet.gapLimit, wallet.watchLast, wallet.birthDate, policy.id, policy.name, policy.script from wallet left join policy on wallet.defaultPolicy = policy.id where wallet.id = 1")
    @RegisterRowMapper(WalletMapper.class)
    Wallet loadMainWallet();

    @SqlQuery("select wallet.id, wallet.name, wallet.label, wallet.network, wallet.policyType, wallet.scriptType, wallet.storedBlockHeight, wallet.gapLimit, wallet.watchLast, wallet.birthDate, policy.id, policy.name, policy.script from wallet left join policy on wallet.defaultPolicy = policy.id where wallet.id != 1")
    @RegisterRowMapper(WalletMapper.class)
    List<Wallet> loadChildWallets();

    @SqlUpdate("insert into wallet (name, label, network, policyType, scriptType, storedBlockHeight, gapLimit, watchLast, birthDate, defaultPolicy) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insert(String name, String label, int network, int policyType, int scriptType, Integer storedBlockHeight, Integer gapLimit, Integer watchLast, Date birthDate, long defaultPolicy);

    @SqlUpdate("update wallet set name = :name where id = :id")
    void updateName(@Bind("id") long id, @Bind("name") String name);

    @SqlUpdate("update wallet set label = :label where id = :id")
    void updateLabel(@Bind("id") long id, @Bind("label") String label);

    @SqlUpdate("update wallet set storedBlockHeight = :blockHeight where id = :id")
    void updateStoredBlockHeight(@Bind("id") long id, @Bind("blockHeight") Integer blockHeight);

    @SqlUpdate("update wallet set gapLimit = :gapLimit where id = :id")
    void updateGapLimit(@Bind("id") long id, @Bind("gapLimit") Integer gapLimit);

    @SqlUpdate("update wallet set watchLast = :watchLast where id = :id")
    void updateWatchLast(@Bind("id") long id, @Bind("watchLast") Integer watchLast);

    @SqlUpdate("set schema ?")
    int setSchema(String schema);

    default Wallet getMainWallet(String schema, String walletName) {
        try {
            setSchema(schema);
            Wallet mainWallet = loadMainWallet();
            if(mainWallet != null) {
                loadWallet(mainWallet);

                if(walletName != null && !walletName.equals(mainWallet.getName())) {
                    mainWallet.setName(walletName);
                    updateName(mainWallet.getId(), walletName);
                }
            }

            return mainWallet;
        } finally {
            setSchema(DbPersistence.DEFAULT_SCHEMA);
        }
    }

    default List<Wallet> getChildWallets(String schema) {
        try {
            List<Wallet> childWallets = loadChildWallets();
            for(Wallet childWallet : childWallets) {
                loadWallet(childWallet);
            }

            return childWallets;
        } finally {
            setSchema(DbPersistence.DEFAULT_SCHEMA);
        }
    }

    default void loadWallet(Wallet wallet) {
        wallet.getKeystores().addAll(createKeystoreDao().getForWalletId(wallet.getId()));

        List<WalletNode> walletNodes = createWalletNodeDao().getForWalletId(wallet.getScriptType().ordinal(), wallet.getId());
        wallet.getPurposeNodes().addAll(walletNodes.stream().filter(walletNode -> walletNode.getDerivation().size() == 1).collect(Collectors.toList()));
        wallet.getPurposeNodes().forEach(walletNode -> walletNode.setWallet(wallet));

        Map<Sha256Hash, BlockTransaction> blockTransactions = createBlockTransactionDao().getForWalletId(wallet.getId());
        wallet.updateTransactions(blockTransactions);

        Map<String, String> detachedLabels = createDetachedLabelDao().getAll();
        wallet.getDetachedLabels().putAll(detachedLabels);

        wallet.setWalletConfig(createWalletConfigDao().getForWalletId(wallet.getId()));
        wallet.setMixConfig(createMixConfigDao().getForWalletId(wallet.getId()));

        Map<Sha256Hash, UtxoMixData> utxoMixes = createUtxoMixDataDao().getForWalletId(wallet.getId());
        wallet.getUtxoMixes().putAll(utxoMixes);
    }

    default void addWallet(String schema, Wallet wallet) {
        try {
            setSchema(schema);
            createPolicyDao().addPolicy(wallet.getDefaultPolicy());

            long id = insert(truncate(wallet.getName()), truncate(wallet.getLabel()), wallet.getNetwork().ordinal(), wallet.getPolicyType().ordinal(), wallet.getScriptType().ordinal(), wallet.getStoredBlockHeight(), wallet.gapLimit(), wallet.getWatchLast(), wallet.getBirthDate(), wallet.getDefaultPolicy().getId());
            wallet.setId(id);

            createKeystoreDao().addKeystores(wallet);
            createWalletNodeDao().addWalletNodes(wallet);
            createBlockTransactionDao().addBlockTransactions(wallet);
            createDetachedLabelDao().clearAndAddAll(wallet);
            createWalletConfigDao().addWalletConfig(wallet);
            createMixConfigDao().addMixConfig(wallet);
            createUtxoMixDataDao().addUtxoMixData(wallet);
        } finally {
            setSchema(DbPersistence.DEFAULT_SCHEMA);
        }
    }

    default String truncate(String label) {
        return (label != null && label.length() > 255 ? label.substring(0, 255) : label);
    }
}
