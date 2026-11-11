package com.sparrowwallet.sparrow.io.db;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.IOUtils;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.Argon2KeyDeriver;
import com.sparrowwallet.drongo.crypto.AsymmetricKeyDeriver;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.*;
import com.sparrowwallet.sparrow.wallet.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.h2.api.JavaObjectSerializer;
import org.h2.mvstore.Cursor;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.db.ValueDataType;
import org.h2.mvstore.tx.TransactionStore;
import org.h2.mvstore.tx.VersionedValueType;
import org.h2.mvstore.type.BasicDataType;
import org.h2.mvstore.type.DataType;
import org.h2.mvstore.type.LongDataType;
import org.h2.mvstore.type.MetaType;
import org.h2.mvstore.type.StringDataType;
import org.h2.result.RowFactory;
import org.h2.result.SearchRow;
import org.h2.tools.ChangeFileEncryption;
import org.h2.util.JdbcUtils;
import org.h2.value.CompareMode;
import org.h2.value.TypeInfo;
import org.h2.value.Typed;
import org.h2.value.Value;
import org.h2.value.VersionedValue;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DbPersistence implements Persistence {
    private static final Logger log = LoggerFactory.getLogger(DbPersistence.class);

    static final String DEFAULT_SCHEMA = "PUBLIC";
    private static final String WALLET_SCHEMA_PREFIX = "wallet_";
    private static final String MASTER_SCHEMA = WALLET_SCHEMA_PREFIX + "master";
    private static final byte[] H2_ENCRYPT_HEADER = "H2encrypt\n".getBytes(StandardCharsets.UTF_8);
    private static final int H2_ENCRYPT_SALT_LENGTH_BYTES = 8;
    private static final int SALT_LENGTH_BYTES = 16;
    public static final byte[] HEADER_MAGIC_1 = "SPRW1\n".getBytes(StandardCharsets.UTF_8);
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";
    public static final String MIGRATION_RESOURCES_DIR = "com/sparrowwallet/sparrow/sql/";
    private static final Pattern JDBC_URL_INJECTION_PATTERN = Pattern.compile(";");
    private static final String H2_META_TABLE_MAP = "table.0";
    private static final Typed[] H2_META_TABLE_COLUMNS = {TypeInfo.TYPE_INTEGER, TypeInfo.TYPE_INTEGER, TypeInfo.TYPE_INTEGER, TypeInfo.TYPE_VARCHAR};
    private static final int H2_META_TABLE_SQL_COLUMN = 3;
    private static final String H2_TYPE_REGISTRY_MAP = "_";
    private static final String H2_PREPARED_TRANSACTIONS_MAP = "openTransactions";
    private static final String H2_TABLE_MAP_PREFIX = "table.";
    private static final String H2_INDEX_MAP_PREFIX = "index.";
    private static final String H2_ROW_DATA_TYPE = "org.h2.mvstore.db.RowDataType$Factory";
    private static final String H2_VALUE_DATA_TYPE = "org.h2.mvstore.db.ValueDataType$Factory";
    private static final String H2_VERSIONED_VALUE_TYPE = "org.h2.mvstore.tx.VersionedValueType$Factory";
    private static final Set<String> VALID_STORE_DATA_TYPES = Set.of(H2_ROW_DATA_TYPE, H2_VALUE_DATA_TYPE, H2_VERSIONED_VALUE_TYPE, "org.h2.mvstore.db.NullValueDataType",
            "org.h2.mvstore.type.LongDataType", "org.h2.mvstore.type.ByteArrayDataType", "org.h2.mvstore.db.LobStorageMap$BlobMeta$Type", "org.h2.mvstore.db.LobStorageMap$BlobReference$Type");
    private static final int MAX_DATA_TYPE_NAME_LENGTH = 256;
    private static final String DDL_NAME = "(?:\"[^\"]*+\"|U&\"(?:[^\"\\\\]|\\\\\\\\|\\\\(?!00[2-6]|007[0-9a-eA-E])[0-9a-fA-F]{4}|\\\\\\+(?:0[1-9a-fA-F]|10)[0-9a-fA-F]{4})*+\")";
    private static final String DDL_OBJECT = DDL_NAME + "\\." + DDL_NAME;
    private static final String DDL_COLUMNS = "\\(" + DDL_NAME + "(?:, " + DDL_NAME + ")*+\\)";
    private static final String DDL_INDEX_COLUMNS = "\\(" + DDL_NAME + "(?: NULLS FIRST)?(?:, " + DDL_NAME + "(?: NULLS FIRST)?)*+\\)";
    private static final String DDL_TYPE = "(?:INTEGER|BIGINT|BOOLEAN|TIMESTAMP|DOUBLE PRECISION ARRAY|BINARY\\(\\d+\\)|(?:CHARACTER|BINARY) VARYING(?:\\(\\d+\\))?)";
    private static final String DDL_COLUMN = DDL_NAME + " " + DDL_TYPE + "(?: DEFAULT (?:\\d+|CURRENT_TIMESTAMP)| GENERATED BY DEFAULT AS IDENTITY SEQUENCE " + DDL_OBJECT + ")?"
            + "(?: SELECTIVITY \\d+)?(?: NOT NULL)?";
    private static final String DDL_CONSTRAINT = "ALTER TABLE " + DDL_OBJECT + " ADD CONSTRAINT " + DDL_OBJECT;
    private static final Pattern VALID_SCHEMA_DDL_PATTERN = Pattern.compile("SET CREATE_BUILD \\d+"
            + "|CREATE USER IF NOT EXISTS " + DDL_NAME + " SALT '[0-9a-f]*' HASH '[0-9a-f]*' ADMIN"
            + "|CREATE SCHEMA IF NOT EXISTS " + DDL_NAME + " AUTHORIZATION " + DDL_NAME
            + "|CREATE SEQUENCE " + DDL_OBJECT + " START WITH \\d+(?: RESTART WITH \\d+)? BELONGS_TO_TABLE"
            + "|CREATE CACHED TABLE " + DDL_OBJECT + "\\(\n    " + DDL_COLUMN + "(?:,\n    " + DDL_COLUMN + ")*+\n\\)"
            + "|CREATE (?:PRIMARY KEY|(?:UNIQUE )?INDEX) " + DDL_OBJECT + " ON " + DDL_OBJECT + DDL_INDEX_COLUMNS
            + "|" + DDL_CONSTRAINT + " (?:PRIMARY KEY|UNIQUE)" + DDL_COLUMNS + "(?: INDEX " + DDL_OBJECT + ")?"
            + "|" + DDL_CONSTRAINT + " FOREIGN KEY" + DDL_COLUMNS + "(?: INDEX " + DDL_OBJECT + ")? REFERENCES " + DDL_OBJECT + DDL_COLUMNS + " NOCHECK");
    private static final Map<String, String> VALID_COLUMN_DEFAULTS = Map.of("UTXOMIXDATA.MIXESDONE", "0", "FLYWAY_SCHEMA_HISTORY.INSTALLED_ON", "CURRENT_TIMESTAMP");
    private static final String H2_BASE_TABLE_CLASS = "org.h2.mvstore.db.MVTable";
    private static final String H2_ALLOWED_CLASSES_PROPERTY = "h2.allowedClasses";
    private static final String H2_NO_ALLOWED_CLASSES = "com.sparrowwallet.sparrow.NONE";

    static {
        System.setProperty(H2_ALLOWED_CLASSES_PROPERTY, H2_NO_ALLOWED_CLASSES);
        JdbcUtils.serializer = new NoDeserializationSerializer();
    }

    private HikariDataSource dataSource;
    private AsymmetricKeyDeriver keyDeriver;

    private Wallet masterWallet;
    private final Map<Wallet, DirtyPersistables> dirtyPersistablesMap = new HashMap<>();
    private ExecutorService updateExecutor;

    public DbPersistence() {
        EventManager.get().register(this);
    }

    @Override
    public WalletAndKey loadWallet(Storage storage) throws IOException, StorageException {
        return loadWallet(storage, null, null);
    }

    @Override
    public WalletAndKey loadWallet(Storage storage, CharSequence password) throws IOException, StorageException {
        return loadWallet(storage, password, null);
    }

    @Override
    public WalletAndKey loadWallet(Storage storage, CharSequence password, ECKey alreadyDerivedKey) throws IOException, StorageException {
        ECKey encryptionKey = getEncryptionKey(password, storage.getWalletFile(), alreadyDerivedKey);

        try {
            validateSchema(storage, MASTER_SCHEMA, encryptionKey);
            migrate(storage, MASTER_SCHEMA, encryptionKey);
            validateSchema(storage, MASTER_SCHEMA, encryptionKey);

            Jdbi jdbi = getJdbi(storage, getFilePassword(encryptionKey));
            masterWallet = jdbi.withHandle(handle -> {
                WalletDao walletDao = handle.attach(WalletDao.class);
                return walletDao.getMainWallet(MASTER_SCHEMA, getWalletName(storage.getWalletFile(), null));
            });

            if(masterWallet == null) {
                throw new StorageException("The wallet file was corrupted. Check the backups folder for previous copies.");
            }

            Map<WalletAndKey, Storage> childWallets = loadChildWallets(storage, masterWallet, encryptionKey);
            masterWallet.setChildWallets(childWallets.keySet().stream().map(WalletAndKey::getWallet).collect(Collectors.toList()));

            createUpdateExecutor(masterWallet);

            return new WalletAndKey(masterWallet, encryptionKey, keyDeriver, childWallets);
        } catch(StorageException | RuntimeException e) {
            closeDataSource();
            throw e;
        }
    }

    private Map<WalletAndKey, Storage> loadChildWallets(Storage storage, Wallet masterWallet, ECKey encryptionKey) throws StorageException {
        Jdbi jdbi = getJdbi(storage, getFilePassword(encryptionKey));
        List<String> schemas = jdbi.withHandle(handle -> {
           return handle.createQuery("show schemas").mapTo(String.class).list();
        });

        List<String> childSchemas = schemas.stream().filter(schema -> schema.startsWith(WALLET_SCHEMA_PREFIX) && !schema.equals(MASTER_SCHEMA)).collect(Collectors.toList());
        Map<WalletAndKey, Storage> childWallets = new TreeMap<>();
        for(String schema : childSchemas) {
            validateSchema(storage, schema, encryptionKey);
            migrate(storage, schema, encryptionKey);
            validateSchema(storage, schema, encryptionKey);

            Jdbi childJdbi = getJdbi(storage, getFilePassword(encryptionKey));
            Wallet wallet = childJdbi.withHandle(handle -> {
                WalletDao walletDao = handle.attach(WalletDao.class);
                Wallet childWallet = walletDao.getMainWallet(schema, null);
                childWallet.setName(schema.substring(WALLET_SCHEMA_PREFIX.length()));
                childWallet.setMasterWallet(masterWallet);
                return childWallet;
            });
            childWallets.put(new WalletAndKey(wallet, encryptionKey, keyDeriver, Collections.emptyMap()), storage);
        }

        return childWallets;
    }

    @Override
    public File storeWallet(Storage storage, Wallet wallet) throws IOException, StorageException {
        File walletFile = storage.getWalletFile();
        walletFile = renameToDbFile(walletFile);

        updatePassword(storage, null);
        cleanAndAddWallet(storage, wallet, null);

        return walletFile;
    }

    @Override
    public File storeWallet(Storage storage, Wallet wallet, ECKey encryptionPubKey) throws IOException, StorageException {
        File walletFile = storage.getWalletFile();
        walletFile = renameToDbFile(walletFile);

        boolean existing = walletFile.exists();
        updatePassword(storage, encryptionPubKey);
        cleanAndAddWallet(storage, wallet, getFilePassword(encryptionPubKey));
        if(!existing) {
            writeBinaryHeader(walletFile);
        }

        return walletFile;
    }

    @Override
    public void updateWallet(Storage storage, Wallet wallet) throws StorageException {
        updateWallet(storage, wallet, null);
    }

    @Override
    public void updateWallet(Storage storage, Wallet wallet, ECKey encryptionPubKey) throws StorageException {
        updateExecutor.execute(() -> {
            try {
                //An update can wait behind others while the wallet is saved with a password added or removed. The key it was queued with is then
                //no longer the key of the storage, and it leaves the encryption of the file as it finds it
                ECKey storagePubKey = storage.getEncryptionPubKey();
                boolean queuedWithStorageKey = Objects.equals(encryptionPubKey, Storage.NO_PASSWORD_KEY.equals(storagePubKey) ? null : storagePubKey);
                String newPassword = getFilePassword(encryptionPubKey);
                String currentPassword = getDatasourcePassword();

                if(!queuedWithStorageKey) {
                    update(storage, wallet, currentPassword);
                } else if(dataSource != null && currentPassword != null && newPassword == null) {
                    //Removing encryption: write data first
                    update(storage, wallet, currentPassword);
                    updatePassword(storage, encryptionPubKey);
                } else {
                    //Adding encryption or no change: change file first
                    updatePassword(storage, encryptionPubKey);
                    update(storage, wallet, newPassword);
                }
            } catch(Exception e) {
                log.error("Error updating wallet db", e);
            }
        });
    }

    private synchronized void createUpdateExecutor(Wallet masterWallet) {
        if(updateExecutor == null) {
            BasicThreadFactory factory = BasicThreadFactory.builder().namingPattern(masterWallet.getFullName() + "-dbupdater").daemon(true).priority(Thread.NORM_PRIORITY).build();
            updateExecutor = Executors.newSingleThreadExecutor(factory);
        }
    }

    private File renameToDbFile(File walletFile) throws IOException {
        if(!walletFile.getName().endsWith("." + getType().getExtension())) {
            File dbFile = new File(walletFile.getParentFile(), walletFile.getName() + "." + getType().getExtension());
            if(walletFile.exists()) {
                if(!walletFile.renameTo(dbFile)) {
                    throw new IOException("Could not rename " + walletFile.getName() + " to " + dbFile.getName());
                }
            }

            return dbFile;
        }

        return walletFile;
    }

    private void update(Storage storage, Wallet wallet, String password) throws StorageException {
        DirtyPersistables dirtyPersistables = dirtyPersistablesMap.get(wallet);
        if(dirtyPersistables == null) {
            return;
        }

        log.debug("Updating " + wallet.getFullName() + " on " + Thread.currentThread().getName());
        log.debug(dirtyPersistables.toString());

        Jdbi jdbi = getJdbi(storage, password);
        List<String> schemas = jdbi.withHandle(handle -> {
            return handle.createQuery("show schemas").mapTo(String.class).list();
        });
        if(!schemas.contains(getSchema(wallet))) {
            log.debug("Not persisting update for missing schema " + getSchema(wallet));
            return;
        }

        jdbi.useHandle(handle -> {
            WalletDao walletDao = handle.attach(WalletDao.class);
            try {
                if(dirtyPersistables.deleteAccount && !wallet.isMasterWallet()) {
                    handle.execute("drop schema `" + getSchema(wallet).replace("`", "``") + "` cascade");
                    return;
                }

                walletDao.setSchema(getSchema(wallet));

                if(dirtyPersistables.clearHistory) {
                    WalletNodeDao walletNodeDao = handle.attach(WalletNodeDao.class);
                    BlockTransactionDao blockTransactionDao = handle.attach(BlockTransactionDao.class);
                    DetachedLabelDao detachedLabelDao = handle.attach(DetachedLabelDao.class);
                    detachedLabelDao.clearAndAddAll(wallet);
                    walletNodeDao.clearHistory(wallet);
                    blockTransactionDao.clear(wallet.getId());
                }

                if(!dirtyPersistables.historyNodes.isEmpty()) {
                    WalletNodeDao walletNodeDao = handle.attach(WalletNodeDao.class);
                    BlockTransactionDao blockTransactionDao = handle.attach(BlockTransactionDao.class);
                    Set<Sha256Hash> referencedTxIds = new HashSet<>();
                    for(WalletNode addressNode : dirtyPersistables.historyNodes) {
                        if(addressNode.getId() == null) {
                            WalletNode purposeNode = wallet.getNode(addressNode.getKeyPurpose());
                            if(purposeNode.getId() == null) {
                                long purposeNodeId = walletNodeDao.insertWalletNode(purposeNode.getDerivationPath(), purposeNode.getLabel(), wallet.getId(), null, null, null);
                                purposeNode.setId(purposeNodeId);
                            }

                            long nodeId = walletNodeDao.insertWalletNode(addressNode.getDerivationPath(), addressNode.getLabel(), wallet.getId(), purposeNode.getId(), addressNode.getAddressData(), addressNode.getSilentPaymentTweak());
                            addressNode.setId(nodeId);
                        } else if(addressNode.getAddress() != null) {
                            walletNodeDao.updateNodeAddressData(addressNode.getId(), addressNode.getAddressData());
                        }

                        List<BlockTransactionHashIndex> txos = addressNode.getTransactionOutputs().stream().flatMap(txo -> txo.isSpent() ? Stream.of(txo, txo.getSpentBy()) : Stream.of(txo)).collect(Collectors.toList());
                        List<Long> existingIds = txos.stream().map(Persistable::getId).filter(Objects::nonNull).collect(Collectors.toList());
                        referencedTxIds.addAll(txos.stream().map(BlockTransactionHash::getHash).collect(Collectors.toSet()));

                        walletNodeDao.deleteNodeTxosNotInList(addressNode, existingIds.isEmpty() ? List.of(-1L) : existingIds);
                        for(BlockTransactionHashIndex txo : addressNode.getTransactionOutputs()) {
                            walletNodeDao.addOrUpdate(addressNode, txo);
                        }
                    }
                    for(Sha256Hash txid : referencedTxIds) {
                        BlockTransaction blkTx = wallet.getTransactions().get(txid);
                        //May be null for a nested wallet if still updating
                        if(blkTx != null) {
                            blockTransactionDao.addOrUpdate(wallet, txid, blkTx);
                        }
                    }
                    if(!dirtyPersistables.clearHistory) {
                        DetachedLabelDao detachedLabelDao = handle.attach(DetachedLabelDao.class);
                        detachedLabelDao.clearAndAddAll(wallet);
                    }
                }

                if(dirtyPersistables.label != null) {
                    walletDao.updateLabel(wallet.getId(), dirtyPersistables.label.length() > Wallet.MAX_LABEL_LENGTH ? dirtyPersistables.label.substring(0, Wallet.MAX_LABEL_LENGTH) : dirtyPersistables.label);
                }

                if(dirtyPersistables.blockHeight != null) {
                    walletDao.updateStoredBlockHeight(wallet.getId(), dirtyPersistables.blockHeight);
                }

                if(dirtyPersistables.gapLimit != null) {
                    walletDao.updateGapLimit(wallet.getId(), dirtyPersistables.gapLimit);
                }

                if(dirtyPersistables.watchLast != null) {
                    walletDao.updateWatchLast(wallet.getId(), dirtyPersistables.watchLast);
                }

                if(!dirtyPersistables.labelEntries.isEmpty()) {
                    BlockTransactionDao blockTransactionDao = handle.attach(BlockTransactionDao.class);
                    WalletNodeDao walletNodeDao = handle.attach(WalletNodeDao.class);
                    for(Entry entry : dirtyPersistables.labelEntries) {
                        if(entry instanceof TransactionEntry && ((TransactionEntry)entry).getBlockTransaction().getId() != null) {
                            blockTransactionDao.updateLabel(((TransactionEntry)entry).getBlockTransaction().getId(), blockTransactionDao.truncate(entry.getLabel()));
                        } else if(entry instanceof NodeEntry) {
                            WalletNode addressNode = ((NodeEntry)entry).getNode();
                            if(addressNode.getId() == null) {
                                WalletNode purposeNode = wallet.getNode(addressNode.getKeyPurpose());
                                if(purposeNode.getId() == null) {
                                    long purposeNodeId = walletNodeDao.insertWalletNode(purposeNode.getDerivationPath(), purposeNode.getLabel(), wallet.getId(), null, null, null);
                                    purposeNode.setId(purposeNodeId);
                                }

                                long nodeId = walletNodeDao.insertWalletNode(addressNode.getDerivationPath(), addressNode.getLabel(), wallet.getId(), purposeNode.getId(), addressNode.getAddressData(), addressNode.getSilentPaymentTweak());
                                addressNode.setId(nodeId);
                            } else if(addressNode.getAddress() != null) {
                                walletNodeDao.updateNodeAddressData(addressNode.getId(), addressNode.getAddressData());
                            }

                            walletNodeDao.updateNodeLabel(addressNode.getId(), walletNodeDao.truncate(entry.getLabel()));
                        } else if(entry instanceof HashIndexEntry && ((HashIndexEntry)entry).getHashIndex().getId() != null) {
                            walletNodeDao.updateTxoLabel(((HashIndexEntry)entry).getHashIndex().getId(), walletNodeDao.truncate(entry.getLabel()));
                        }
                    }
                }

                if(!dirtyPersistables.utxoStatuses.isEmpty()) {
                    WalletNodeDao walletNodeDao = handle.attach(WalletNodeDao.class);
                    for(BlockTransactionHashIndex utxo : dirtyPersistables.utxoStatuses) {
                        walletNodeDao.updateTxoStatus(utxo.getId(), utxo.getStatus() == null ? null : utxo.getStatus().ordinal());
                    }
                }

                if(dirtyPersistables.walletConfig) {
                    WalletConfigDao walletConfigDao = handle.attach(WalletConfigDao.class);
                    walletConfigDao.addOrUpdate(wallet, wallet.getWalletConfig());
                }

                if(dirtyPersistables.silentPaymentAddresses) {
                    SilentPaymentAddressDao silentPaymentAddressDao = handle.attach(SilentPaymentAddressDao.class);
                    silentPaymentAddressDao.clearAndAddAll(wallet);
                }

                if(dirtyPersistables.walletTable != null) {
                    WalletTableDao walletTableDao = handle.attach(WalletTableDao.class);
                    walletTableDao.addOrUpdate(wallet, dirtyPersistables.walletTable.getTableType(), dirtyPersistables.walletTable);
                }

                if(dirtyPersistables.mixConfig) {
                    MixConfigDao mixConfigDao = handle.attach(MixConfigDao.class);
                    mixConfigDao.addOrUpdate(wallet, wallet.getMixConfig());
                }

                if(!dirtyPersistables.changedUtxoMixes.isEmpty()) {
                    UtxoMixDataDao utxoMixDataDao = handle.attach(UtxoMixDataDao.class);
                    for(Map.Entry<Sha256Hash, UtxoMixData> utxoMixDataEntry : dirtyPersistables.changedUtxoMixes.entrySet()) {
                        utxoMixDataDao.addOrUpdate(wallet, utxoMixDataEntry.getKey(), utxoMixDataEntry.getValue());
                    }
                }

                if(!dirtyPersistables.removedUtxoMixes.isEmpty()) {
                    UtxoMixDataDao utxoMixDataDao = handle.attach(UtxoMixDataDao.class);
                    List<Long> ids = dirtyPersistables.removedUtxoMixes.values().stream().map(Persistable::getId).filter(Objects::nonNull).collect(Collectors.toList());
                    utxoMixDataDao.deleteUtxoMixData(ids);
                }

                if(!dirtyPersistables.labelKeystores.isEmpty()) {
                    KeystoreDao keystoreDao = handle.attach(KeystoreDao.class);
                    for(Keystore keystore : dirtyPersistables.labelKeystores) {
                        keystoreDao.updateLabel(keystore.getLabel(), keystore.getId());
                    }
                }

                if(!dirtyPersistables.encryptionKeystores.isEmpty()) {
                    KeystoreDao keystoreDao = handle.attach(KeystoreDao.class);
                    for(Keystore keystore : dirtyPersistables.encryptionKeystores) {
                        keystoreDao.updateKeystoreEncryption(keystore);
                    }
                }

                if(!dirtyPersistables.registrationKeystores.isEmpty()) {
                    KeystoreDao keystoreDao = handle.attach(KeystoreDao.class);
                    for(Keystore keystore : dirtyPersistables.registrationKeystores) {
                        keystoreDao.updateDeviceRegistration(keystore.getDeviceRegistration(), keystore.getId());
                    }
                }

                dirtyPersistablesMap.remove(wallet);
            } finally {
                walletDao.setSchema(DEFAULT_SCHEMA);
            }
        });
    }

    private void cleanAndAddWallet(Storage storage, Wallet wallet, String password) throws StorageException {
        String schema = getSchema(wallet);
        cleanAndMigrate(storage, schema, password);

        Jdbi jdbi = getJdbi(storage, password);
        jdbi.useHandle(handle -> {
            WalletDao walletDao = handle.attach(WalletDao.class);
            walletDao.addWallet(schema, wallet);
        });

        if(wallet.isMasterWallet()) {
            masterWallet = wallet;
            createUpdateExecutor(masterWallet);
        }
    }

    private void validateStore(File walletFile, String filePassword) throws StorageException {
        if(!walletFile.exists()) {
            return;
        }

        boolean storeOpened = false;
        List<String> schemaDdl = null;
        try {
            try(MVStore store = openValidationStore(walletFile, filePassword)) {
                storeOpened = true;
                if(store.hasData(H2_PREPARED_TRANSACTIONS_MAP)) {
                    throw new StorageException("This is not a valid wallet file.\n\nWallet file contains unexpected database objects.");
                }

                if(store.hasMap(H2_TYPE_REGISTRY_MAP)) {
                    MVMap<String, String> typeRegistry = store.openMap(H2_TYPE_REGISTRY_MAP, new MVMap.Builder<String, String>().keyType(StringDataType.INSTANCE).valueType(new DataTypeNames()));
                    Cursor<String, String> registryCursor = typeRegistry.cursor(null);
                    while(registryCursor.hasNext()) {
                        registryCursor.next();
                    }
                }
            }

            try(MVStore store = openValidationStore(walletFile, filePassword)) {
                if(store.getMapNames().contains(H2_META_TABLE_MAP)) {
                    int metaTableId = DataUtils.parseHexInt(store.getMetaMap().get(DataUtils.META_NAME + H2_META_TABLE_MAP));
                    UndoRecords undoRecords = null;
                    for(String mapName : new ArrayList<>(store.getMapNames())) {
                        if(mapName.startsWith(TransactionStore.UNDO_LOG_NAME_PREFIX) && store.hasData(mapName)) {
                            if(undoRecords == null) {
                                undoRecords = new UndoRecords(store);
                            }
                            Cursor<Long, Integer> undoCursor = store.openMap(mapName, new MVMap.Builder<Long, Integer>().keyType(LongDataType.INSTANCE).valueType(undoRecords)).cursor(null);
                            while(undoCursor.hasNext()) {
                                undoCursor.next();
                            }
                        }
                    }

                    MVMap.Builder<Long, VersionedValue<?>> metaTableBuilder = new MVMap.Builder<>();
                    if(undoRecords != null && undoRecords.mapIds.contains(metaTableId)) {
                        metaTableBuilder.keyType((DataType)undoRecords.getKeyType(metaTableId)).valueType((DataType)undoRecords.getValueType(metaTableId));
                    } else {
                        RowFactory rowFactory = RowFactory.getDefaultRowFactory().createRowFactory(null, CompareMode.getInstance(null, 0), null, H2_META_TABLE_COLUMNS, null, false);
                        metaTableBuilder.keyType(LongDataType.INSTANCE).valueType((DataType)new VersionedValueType<>(rowFactory.getRowDataType()));
                    }
                    MVMap<Long, VersionedValue<?>> metaTable = store.openMap(H2_META_TABLE_MAP, metaTableBuilder);
                    List<String> statements = new ArrayList<>();
                    Cursor<Long, VersionedValue<?>> cursor = metaTable.cursor(null);
                    while(cursor.hasNext()) {
                        cursor.next();
                        for(Object value : Arrays.asList(cursor.getValue().getCurrentValue(), cursor.getValue().getCommittedValue())) {
                            if(value != null) {
                                Value sql = ((SearchRow)value).getValue(H2_META_TABLE_SQL_COLUMN);
                                statements.add(sql != null && sql.getValueType() == Value.VARCHAR ? sql.getString() : null);
                            }
                        }
                    }

                    schemaDdl = statements;
                }
            }
        } catch(MVStoreException e) {
            if(schemaDdl != null) {
                log.warn("Error closing wallet file store after validation", e);
            } else if(!storeOpened) {
                log.debug("Could not open wallet file store for validation, deferring to standard open", e);
                return;
            } else {
                log.error("Error validating wallet file", e);
                throw new StorageException("This is not a valid wallet file.\n\nWallet file could not be validated.");
            }
        } catch(RuntimeException e) {
            log.error("Error validating wallet file", e);
            throw new StorageException("This is not a valid wallet file.\n\nWallet file could not be validated.");
        }

        if(schemaDdl == null) {
            throw new StorageException("This is not a valid wallet file.\n\nWallet file could not be validated.");
        }

        for(String statement : schemaDdl) {
            if(statement == null || !VALID_SCHEMA_DDL_PATTERN.matcher(statement).matches()) {
                String loggedStatement = statement == null ? null : statement.replaceAll("'[^']*'", "'?'");
                log.error("Wallet file contains unexpected statement: " + (loggedStatement != null && loggedStatement.length() > 1000 ? loggedStatement.substring(0, 1000) + "..." : loggedStatement));
                throw new StorageException("This is not a valid wallet file.\n\nWallet file contains unexpected database objects.");
            }
        }
    }

    private MVStore openValidationStore(File walletFile, String filePassword) {
        MVStore.Builder builder = new MVStore.Builder().fileName(walletFile.getAbsolutePath()).readOnly();
        if(filePassword != null) {
            builder.encryptionKey(filePassword.toCharArray());
        }

        return builder.open();
    }

    private void migrate(Storage storage, String schema, ECKey encryptionKey) throws StorageException {
        File migrationDir = getMigrationDir();
        try {
            Flyway flyway = getFlyway(storage, schema, getFilePassword(encryptionKey), migrationDir);
            flyway.migrate();
        } catch(FlywayValidateException e) {
            log.error("Failed to open wallet file. Validation error during schema migration.", e);
            throw new StorageException("Failed to open wallet file. Validation error during schema migration.", e);
        } catch(FlywayException e) {
            log.error("Failed to open wallet file. ", e);
            throw new StorageException("Failed to open wallet file.\n" + e.getMessage(), e);
        } finally {
            IOUtils.deleteDirectory(migrationDir);
        }
    }

    private void validateSchema(Storage storage, String schema, ECKey encryptionKey) throws StorageException {
        Jdbi jdbi = getJdbi(storage, getFilePassword(encryptionKey));
        try {
            jdbi.useHandle(handle -> {
                List<String> routines = handle.createQuery("SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA <> 'INFORMATION_SCHEMA'").mapTo(String.class).list();
                if(!routines.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected database routines: " + String.join(", ", routines) + "."));
                }

                List<String> triggers = handle.createQuery("SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_SCHEMA <> 'INFORMATION_SCHEMA'").mapTo(String.class).list();
                if(!triggers.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected database triggers: " + String.join(", ", triggers) + "."));
                }

                List<String> checkConstraints = handle.createQuery("SELECT CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS WHERE UPPER(CONSTRAINT_SCHEMA) = UPPER(:schema)")
                        .bind("schema", schema).mapTo(String.class).list();
                if(!checkConstraints.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected check constraints: " + String.join(", ", checkConstraints) + "."));
                }

                List<String> nonBaseTables = handle.createQuery("SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA) = UPPER(:schema) "
                        + "AND TABLE_TYPE <> 'BASE TABLE'").bind("schema", schema)
                        .map((rs, ctx) -> rs.getString("TABLE_NAME") + " (" + rs.getString("TABLE_TYPE") + ")").list();
                if(!nonBaseTables.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected database object types: " + String.join(", ", nonBaseTables) + "."));
                }

                List<String> linkedTables = handle.createQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA) = UPPER(:schema) AND STORAGE_TYPE = 'TABLE LINK'")
                        .bind("schema", schema).mapTo(String.class).list();
                if(!linkedTables.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected linked tables: " + String.join(", ", linkedTables) + "."));
                }

                List<String> engineTables = handle.createQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA) = UPPER(:schema) "
                        + "AND TABLE_TYPE = 'BASE TABLE' AND (TABLE_CLASS IS NULL OR TABLE_CLASS <> :tableClass)").bind("schema", schema).bind("tableClass", H2_BASE_TABLE_CLASS).mapTo(String.class).list();
                if(!engineTables.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected table storage engines: " + String.join(", ", engineTables) + "."));
                }

                List<String> synonyms = handle.createQuery("SELECT SYNONYM_NAME FROM INFORMATION_SCHEMA.SYNONYMS WHERE UPPER(SYNONYM_SCHEMA) = UPPER(:schema)")
                        .bind("schema", schema).mapTo(String.class).list();
                if(!synonyms.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected synonyms: " + String.join(", ", synonyms) + "."));
                }

                List<String> generatedColumns = handle.createQuery("SELECT TABLE_NAME || '.' || COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_SCHEMA) = UPPER(:schema) AND GENERATION_EXPRESSION IS NOT NULL")
                        .bind("schema", schema).mapTo(String.class).list();
                if(!generatedColumns.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected generated columns: " + String.join(", ", generatedColumns) + "."));
                }

                List<String[]> defaultColumns = handle.createQuery("SELECT TABLE_NAME, COLUMN_NAME, COLUMN_DEFAULT, COLUMN_ON_UPDATE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_SCHEMA) = UPPER(:schema) AND (COLUMN_DEFAULT IS NOT NULL OR COLUMN_ON_UPDATE IS NOT NULL)").bind("schema", schema)
                        .map((rs, ctx) -> new String[] {rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME"), rs.getString("COLUMN_DEFAULT"), rs.getString("COLUMN_ON_UPDATE")}).list();
                List<String> unexpectedDefaults = new ArrayList<>();
                for(String[] column : defaultColumns) {
                    String qualifiedName = column[0] + "." + column[1];
                    if(column[3] != null || !Objects.equals(VALID_COLUMN_DEFAULTS.get(qualifiedName.toUpperCase(Locale.ROOT)), column[2])) {
                        unexpectedDefaults.add(qualifiedName);
                    }
                }
                if(!unexpectedDefaults.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected column default or update expressions: " + String.join(", ", unexpectedDefaults) + "."));
                }

                List<String> domains = handle.createQuery("SELECT DOMAIN_NAME FROM INFORMATION_SCHEMA.DOMAINS WHERE DOMAIN_SCHEMA <> 'INFORMATION_SCHEMA'").mapTo(String.class).list();
                if(!domains.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected database domains: " + String.join(", ", domains) + "."));
                }

                List<String> constants = handle.createQuery("SELECT CONSTANT_NAME FROM INFORMATION_SCHEMA.CONSTANTS WHERE CONSTANT_SCHEMA <> 'INFORMATION_SCHEMA'").mapTo(String.class).list();
                if(!constants.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected database constants: " + String.join(", ", constants) + "."));
                }

                List<String> serializedColumns = handle.createQuery("SELECT TABLE_NAME || '.' || COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_SCHEMA) = UPPER(:schema) AND DATA_TYPE = 'JAVA_OBJECT' "
                        + "UNION SELECT OBJECT_NAME FROM INFORMATION_SCHEMA.ELEMENT_TYPES WHERE UPPER(OBJECT_SCHEMA) = UPPER(:schema) AND OBJECT_TYPE = 'TABLE' AND DATA_TYPE = 'JAVA_OBJECT'")
                        .bind("schema", schema).mapTo(String.class).list();
                if(!serializedColumns.isEmpty()) {
                    throw new RuntimeException(new StorageException("Wallet file contains unexpected serialized object columns: " + String.join(", ", serializedColumns) + "."));
                }
            });
        } catch(RuntimeException e) {
            if(e.getCause() instanceof StorageException) {
                throw new StorageException("This is not a valid wallet file.\n\n" + e.getCause().getMessage());
            }
            throw e;
        }
    }

    private void cleanAndMigrate(Storage storage, String schema, String password) throws StorageException {
        File migrationDir = getMigrationDir();
        try {
            Flyway flyway = getFlyway(storage, schema, password, migrationDir);
            flyway.clean();
            flyway.migrate();
        } catch(FlywayException e) {
            log.error("Failed to save wallet file.", e);
            throw new StorageException("Failed to save wallet file.\n" + e.getMessage(), e);
        } finally {
            IOUtils.deleteDirectory(migrationDir);
        }
    }

    private String getSchema(Wallet wallet) {
        return wallet.isMasterWallet() ? MASTER_SCHEMA : WALLET_SCHEMA_PREFIX + wallet.getName();
    }

    private String getFilePassword(ECKey encryptionKey) {
        if(encryptionKey == null) {
            return null;
        }

        return Utils.bytesToHex(encryptionKey.getPubKey());
    }

    private void writeBinaryHeader(File walletFile) throws IOException {
        if(dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        ByteBuffer header = ByteBuffer.allocate(HEADER_MAGIC_1.length + SALT_LENGTH_BYTES);
        header.put(HEADER_MAGIC_1);
        header.put(keyDeriver.getSalt());
        header.flip();

        try(FileChannel fileChannel = new RandomAccessFile(walletFile, "rwd").getChannel()) {
            fileChannel.position(H2_ENCRYPT_HEADER.length + H2_ENCRYPT_SALT_LENGTH_BYTES);
            fileChannel.write(header);
        }
    }

    private void updatePassword(Storage storage, ECKey encryptionPubKey) throws StorageException {
        String newPassword = getFilePassword(encryptionPubKey);
        String currentPassword = getDatasourcePassword();

        //The password only needs to be changed if the datasource is not null - if we have not loaded the wallet from a datasource, it is a new wallet and the database is still to be created
        if(dataSource != null && !Objects.equals(currentPassword, newPassword)) {
            if(!dataSource.isClosed()) {
                dataSource.close();
            }

            try {
                changeFileEncryption(storage.getWalletFile(), currentPassword, newPassword);

                //This sets the new password on the datasource for the next updatePassword check
                getDataSource(storage, newPassword);
            } catch(Exception e) {
                //The wallet file is either unchanged or fully converted, so do not go on to write to it with a password it may not have
                log.error("Error changing database password", e);
                throw new StorageException("Failed to change the password of the wallet file.\n" + e.getMessage(), e);
            }
        }
    }

    //H2's ChangeFileEncryption converts every file in the given directory whose name starts with the database name followed by a dot, which also
    //matches sibling wallet files where the wallet name itself contains a dot. Convert a copy of this wallet file in a temporary directory of its
    //own so that no other file can be touched, and only replace the original once the conversion has been verified. The temporary directory is
    //created alongside the wallet file so that the replacement is an atomic move, and so that the copy is not written to a shared temp location.
    private void changeFileEncryption(File walletFile, String currentPassword, String newPassword) throws IOException, SQLException {
        File dbFile = walletFile.getAbsoluteFile();
        //A wallet file may be a symlink to a file stored elsewhere, so convert and replace the file it points to rather than the link
        Path targetFile = dbFile.toPath().toRealPath();
        Path encryptionDir = Files.createTempDirectory(targetFile.getParent(), "sparrowenc");

        try {
            Path encryptionFile = encryptionDir.resolve(dbFile.getName());
            Files.copy(dbFile.toPath(), encryptionFile);
            ChangeFileEncryption.execute(encryptionDir.toString(), getWalletName(dbFile, null), "AES",
                    currentPassword == null ? null : currentPassword.toCharArray(),
                    newPassword == null ? null : newPassword.toCharArray(), true);

            //Verify the conversion happened before replacing the original
            if(hasEncryptHeader(encryptionFile.toFile()) == (newPassword == null)) {
                throw new IOException("The encryption of the wallet file was not changed");
            }

            //H2 writes a new file header on every conversion, discarding the salt written there previously
            if(newPassword != null) {
                writeBinaryHeader(encryptionFile.toFile());
            }

            try {
                Files.setPosixFilePermissions(encryptionFile, Files.getPosixFilePermissions(dbFile.toPath()));
            } catch(UnsupportedOperationException | IOException e) {
                log.debug("Could not copy permissions to " + encryptionFile, e);
            }

            try {
                Files.move(encryptionFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
            } catch(AtomicMoveNotSupportedException e) {
                Files.move(encryptionFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            File[] remainingFiles = encryptionDir.toFile().listFiles();
            if(remainingFiles != null) {
                for(File remainingFile : remainingFiles) {
                    IOUtils.secureDelete(remainingFile);
                }
            }

            IOUtils.deleteDirectory(encryptionDir.toFile());
        }
    }

    private String getDatasourcePassword() {
        if(dataSource != null) {
            String dsPassword = dataSource.getPassword();
            if(dsPassword.isEmpty()) {
                return null;
            }

            return dsPassword.substring(0, dsPassword.length() - (" " + H2_PASSWORD).length());
        }

        return null;
    }

    @Override
    public boolean isPersisted(Storage storage, Wallet wallet) {
        return wallet.getId() != null;
    }

    @Override
    public ECKey getEncryptionKey(CharSequence password) throws IOException {
        return getEncryptionKey(password, null, null);
    }

    private ECKey getEncryptionKey(CharSequence password, File walletFile, ECKey alreadyDerivedKey) throws IOException {
        if(alreadyDerivedKey != null) {
            return alreadyDerivedKey;
        } else if(password == null) {
            return null;
        } else if(password.equals("")) {
            return Storage.NO_PASSWORD_KEY;
        }

        AsymmetricKeyDeriver keyDeriver = getKeyDeriver(walletFile);
        return keyDeriver.deriveECKey(password);
    }

    @Override
    public AsymmetricKeyDeriver getKeyDeriver() {
        return keyDeriver;
    }

    @Override
    public void setKeyDeriver(AsymmetricKeyDeriver keyDeriver) {
        this.keyDeriver = keyDeriver;
    }

    private AsymmetricKeyDeriver getKeyDeriver(File walletFile) throws IOException {
        if(keyDeriver == null) {
            keyDeriver = getWalletKeyDeriver(walletFile);
        }

        return keyDeriver;
    }

    private AsymmetricKeyDeriver getWalletKeyDeriver(File walletFile) throws IOException {
        if(keyDeriver == null) {
            byte[] salt = new byte[SALT_LENGTH_BYTES];

            if(walletFile != null && walletFile.exists()) {
                try(InputStream inputStream = new FileInputStream(walletFile)) {
                    inputStream.skip(H2_ENCRYPT_HEADER.length + H2_ENCRYPT_SALT_LENGTH_BYTES + HEADER_MAGIC_1.length);
                    inputStream.read(salt, 0, salt.length);
                }
            } else {
                SecureRandom secureRandom = new SecureRandom();
                secureRandom.nextBytes(salt);
            }

            return new Argon2KeyDeriver(salt);
        }

        return keyDeriver;
    }

    @Override
    public boolean isEncrypted(File walletFile) throws IOException {
        if(dataSource != null) {
            return getDatasourcePassword() != null;
        }

        return hasEncryptHeader(walletFile);
    }

    private boolean hasEncryptHeader(File walletFile) throws IOException {
        byte[] header = new byte[H2_ENCRYPT_HEADER.length];
        try(InputStream inputStream = new FileInputStream(walletFile)) {
            inputStream.read(header, 0, H2_ENCRYPT_HEADER.length);
            return Arrays.equals(H2_ENCRYPT_HEADER, header);
        }
    }

    @Override
    public String getWalletId(Storage storage, Wallet wallet) {
        return storage.getWalletFile().getParentFile().getAbsolutePath() + File.separator + getWalletName(storage.getWalletFile(), null) + ":" + (wallet == null || wallet.isMasterWallet() ? "master" : wallet.getName());
    }

    @Override
    public String getWalletName(File walletFile, Wallet wallet) {
        if(wallet != null && wallet.getMasterWallet() != null) {
            return wallet.getName();
        }

        String name = walletFile.getName();
        if(name.endsWith("." + getType().getExtension())) {
            name = name.substring(0, name.length() - getType().getExtension().length() - 1);
        }

        return name;
    }

    @Override
    public PersistenceType getType() {
        return PersistenceType.DB;
    }

    @Override
    public void copyWallet(File walletFile, OutputStream outputStream) throws IOException {
        if(dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        com.google.common.io.Files.copy(walletFile, outputStream);
    }

    @Override
    public boolean isClosed() {
        return dataSource == null || dataSource.isClosed();
    }

    @Override
    public void close() {
        EventManager.get().unregister(this);
        if(updateExecutor != null) {
            updateExecutor.shutdown();
            try {
                if(!updateExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                    updateExecutor.shutdownNow();
                }

                closeDataSource();
            } catch (InterruptedException e) {
                updateExecutor.shutdownNow();
                closeDataSource();
            }
        } else {
            closeDataSource();
        }
    }

    private void closeDataSource() {
        if(dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private Jdbi getJdbi(Storage storage, String password) throws StorageException {
        Jdbi jdbi = Jdbi.create(getDataSource(storage, password));
        jdbi.installPlugin(new H2DatabasePlugin());
        jdbi.installPlugin(new SqlObjectPlugin());

        return jdbi;
    }

    private Flyway getFlyway(Storage storage, String schema, String password, File resourcesDir) throws StorageException {
        if(schema.contains("\"")) {
            throw new StorageException("This is not a valid wallet file.\n\nWallet file contains an invalid account name.");
        }

        return Flyway.configure().cleanDisabled(false).dataSource(getDataSource(storage, password)).locations("filesystem:" + resourcesDir.getAbsolutePath()).schemas(schema)
                .placeholderReplacement(false).failOnMissingLocations(true).load();
    }

    //Flyway does not support JPMS yet, so the migration files are extracted to a temp dir in order to avoid classloader encapsulation issues
    private File getMigrationDir() throws StorageException {
        try {
            File migrationDir = Files.createTempDirectory("sparrowfly").toFile();
            String[] files = IOUtils.getResourceListing(DbPersistence.class, MIGRATION_RESOURCES_DIR);
            for(String name : files) {
                File targetFile = new File(migrationDir, name);
                try(InputStream inputStream = DbPersistence.class.getResourceAsStream("/" + MIGRATION_RESOURCES_DIR + name)) {
                    if(inputStream != null) {
                        Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        log.error("Could not load resource at /" + MIGRATION_RESOURCES_DIR + name);
                    }
                }
            }

            return migrationDir;
        } catch(Exception e) {
            log.error("Could not extract migration resources", e);
            throw new StorageException("Could not extract migration resources", e);
        }
    }

    private HikariDataSource getDataSource(Storage storage, String password) throws StorageException {
        if(dataSource == null || dataSource.isClosed()) {
            dataSource = createDataSource(storage.getWalletFile(), password);
        }

        return dataSource;
    }

    private HikariDataSource createDataSource(File walletFile, String password) throws StorageException {
        try {
            validateStore(walletFile, password);
            Class.forName("org.h2.Driver");
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(getUrl(walletFile, password));
            config.setUsername(H2_USER);
            config.setPassword(password == null ? H2_PASSWORD : password + " " + H2_PASSWORD);
            return new HikariDataSource(config);
        } catch(ClassNotFoundException e) {
            log.error("Cannot find H2 driver", e);
            throw new StorageException("Cannot find H2 driver", e);
        } catch(HikariPool.PoolInitializationException e) {
            if(e.getMessage() != null && e.getMessage().contains("Database may be already in use")) {
                log.error("Wallet file may already be in use. Make sure the application is not running elsewhere.", e);
                throw new StorageException("Wallet file may already be in use. Make sure the application is not running elsewhere.", e);
            } else if(e.getMessage() != null && (e.getMessage().contains("Wrong user name or password") || e.getMessage().contains("Encryption error in file"))) {
                throw new InvalidPasswordException("Incorrect password for wallet file " + walletFile.getAbsolutePath(), e);
            } else {
                log.error("Failed to open database file", e);
                throw new StorageException("Failed to open database file.\n" + e.getMessage(), e);
            }
        }
    }

    private String getUrl(File walletFile, String password) throws StorageException {
        File dbFile = walletFile.getAbsoluteFile();
        if(JDBC_URL_INJECTION_PATTERN.matcher(dbFile.getPath()).find()) {
            throw new StorageException("Wallet file path contains invalid characters");
        }

        //H2 appends the extension to the database name in the URL, so only a trailing extension can be removed - removing every occurrence would
        //open a different file to the one tracked here, as would removing one from a directory name
        File dbName = new File(dbFile.getParentFile(), getWalletName(dbFile, null));
        return "jdbc:h2:" + dbName.getPath() + ";INIT=SET TRACE_LEVEL_FILE=4;TRACE_LEVEL_FILE=4;DEFRAG_ALWAYS=true;MAX_COMPACT_TIME=5000;DATABASE_TO_UPPER=false" + (password == null ? "" : ";CIPHER=AES");
    }

    private boolean persistsFor(Wallet wallet) {
        if(masterWallet != null) {
            if(wallet == masterWallet) {
                return true;
            }

            return masterWallet.getChildWallets().contains(wallet);
        }

        return false;
    }

    @Subscribe
    public void walletDeleted(WalletDeletedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).deleteAccount = true);
        }
    }

    @Subscribe
    public void walletHistoryCleared(WalletHistoryClearedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).clearHistory = true);
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(persistsFor(event.getWallet()) && !event.getHistoryChangedNodes().isEmpty()) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).historyNodes.addAll(event.getHistoryChangedNodes()));
        }
    }

    @Subscribe
    public void walletLabelChanged(WalletLabelChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).label = event.getLabel());
        }
    }

    @Subscribe
    public void walletBlockHeightChanged(WalletBlockHeightChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).blockHeight = event.getBlockHeight());
        }
    }

    @Subscribe
    public void walletGapLimitChanged(WalletGapLimitChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).gapLimit = event.getGapLimit());
        }
    }

    @Subscribe
    public void walletEntryLabelsChanged(WalletEntryLabelsChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).labelEntries.addAll(event.getEntries()));
        }
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).utxoStatuses.addAll(event.getUtxos()));
        }
    }

    @Subscribe
    public void walletConfigChanged(WalletConfigChangedEvent event) {
        if(persistsFor(event.getWallet()) && event.getWallet().getWalletConfig() != null) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).walletConfig = true);
        }
    }

    @Subscribe
    public void walletTableChanged(WalletTableChangedEvent event) {
        if(persistsFor(event.getWallet()) && event.getTableType() != null && event.getWallet().getWalletTable(event.getTableType()) != null) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).walletTable = event.getWalletTable());
        }
    }

    @Subscribe
    public void keystoreLabelsChanged(KeystoreLabelsChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).labelKeystores.addAll(event.getChangedKeystores()));
        }
    }

    @Subscribe
    public void keystoreEncryptionChanged(KeystoreEncryptionChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).encryptionKeystores.addAll(event.getChangedKeystores()));
        }
    }

    @Subscribe
    public void keystoreDeviceRegistrationsChanged(KeystoreDeviceRegistrationsChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).registrationKeystores.addAll(event.getChangedKeystores()));
        }
    }

    @Subscribe
    public void walletWatchLastChanged(WalletWatchLastChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).watchLast = event.getWatchLast());
        }
    }

    @Subscribe
    public void walletSilentPaymentAddressesChanged(WalletSilentPaymentAddressesChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).silentPaymentAddresses = true);
        }
    }

    private static class DirtyPersistables {
        public boolean deleteAccount;
        public boolean clearHistory;
        public final List<WalletNode> historyNodes = new ArrayList<>();
        public String label;
        public Integer blockHeight = null;
        public Integer gapLimit = null;
        public Integer watchLast = null;
        public final List<Entry> labelEntries = new ArrayList<>();
        public final List<BlockTransactionHashIndex> utxoStatuses = new ArrayList<>();
        public boolean walletConfig;
        public WalletTable walletTable = null;
        public boolean mixConfig;
        public final Map<Sha256Hash, UtxoMixData> changedUtxoMixes = new HashMap<>();
        public final Map<Sha256Hash, UtxoMixData> removedUtxoMixes = new HashMap<>();
        public final List<Keystore> labelKeystores = new ArrayList<>();
        public final List<Keystore> encryptionKeystores = new ArrayList<>();
        public final List<Keystore> registrationKeystores = new ArrayList<>();
        public boolean silentPaymentAddresses;

        public String toString() {
            return "Dirty Persistables" +
                    "\nDelete account:" + deleteAccount +
                    "\nClear history:" + clearHistory +
                    "\nNodes:" + historyNodes +
                    "\nLabel:" + label +
                    "\nBlockHeight:" + blockHeight +
                    "\nGap limit:" + gapLimit +
                    "\nWatch last:" + watchLast +
                    "\nTx labels:" + labelEntries.stream().filter(entry -> entry instanceof TransactionEntry).map(entry -> ((TransactionEntry)entry).getBlockTransaction().getHash().toString()).collect(Collectors.toList()) +
                    "\nAddress labels:" + labelEntries.stream().filter(entry -> entry instanceof NodeEntry).map(entry -> ((NodeEntry)entry).getNode().toString() + " " + entry.getLabel()).collect(Collectors.toList()) +
                    "\nUTXO labels:" + labelEntries.stream().filter(entry -> entry instanceof HashIndexEntry).map(entry -> ((HashIndexEntry)entry).getHashIndex().toString()).collect(Collectors.toList()) +
                    "\nUTXO statuses:" + utxoStatuses +
                    "\nWallet config:" + walletConfig +
                    "\nWallet table:" + walletTable +
                    "\nMix config:" + mixConfig +
                    "\nUTXO mixes changed:" + changedUtxoMixes +
                    "\nUTXO mixes removed:" + removedUtxoMixes +
                    "\nKeystore labels:" + labelKeystores.stream().map(Keystore::getLabel).collect(Collectors.toList()) +
                    "\nKeystore encryptions:" + encryptionKeystores.stream().map(Keystore::getLabel).collect(Collectors.toList()) +
                    "\nKeystore registrations:" + registrationKeystores.stream().map(Keystore::getDeviceRegistration).collect(Collectors.toList()) +
                    "\nSilent payment addresses:" + silentPaymentAddresses;
        }
    }

    private static class UndoRecords extends BasicDataType<Integer> {
        private final MVStore store;
        private final MVMap<String, DataType<?>> typeRegistry;
        private final Set<Integer> mapIds = new HashSet<>();

        public UndoRecords(MVStore store) {
            this.store = store;
            this.typeRegistry = store.openMap(H2_TYPE_REGISTRY_MAP, new MVMap.Builder<String, DataType<?>>().keyType(StringDataType.INSTANCE).valueType(new MetaType<>(null, store.backgroundExceptionHandler)));
        }

        @Override
        public Integer read(ByteBuffer buff) {
            int mapId = DataUtils.readVarInt(buff);
            if(mapId >= 0) {
                String mapName = store.getMapName(mapId);
                if(mapName == null || !(mapName.startsWith(H2_TABLE_MAP_PREFIX) || mapName.startsWith(H2_INDEX_MAP_PREFIX))) {
                    throw new IllegalArgumentException("Unexpected undo log map " + mapName);
                }

                getKeyType(mapId).read(buff);
                if(buff.get() == 1) {
                    getValueType(mapId).read(buff);
                }
                mapIds.add(mapId);
            }

            return mapId;
        }

        public DataType<?> getKeyType(int mapId) {
            return getDataType(mapId, "key", new ValueDataType());
        }

        public DataType<?> getValueType(int mapId) {
            return getDataType(mapId, "val", new VersionedValueType<>(new ValueDataType()));
        }

        private DataType<?> getDataType(int mapId, String configKey, DataType<?> defaultType) {
            String typeKey = DataUtils.parseMap(store.getMetaMap().get(DataUtils.META_MAP + Integer.toHexString(mapId))).get(configKey);
            if(typeKey == null) {
                return defaultType;
            }

            DataType<?> dataType = typeRegistry.get(typeKey);
            if(dataType == null) {
                throw new IllegalArgumentException("Unknown data type " + typeKey);
            }

            return dataType;
        }

        @Override
        public int getMemory(Integer obj) {
            return 0;
        }

        @Override
        public void write(WriteBuffer buff, Integer obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Integer[] createStorage(int size) {
            return new Integer[size];
        }
    }

    private static class DataTypeNames extends BasicDataType<String> {
        @Override
        public String read(ByteBuffer buff) {
            return readDataType(buff, false);
        }

        private static String readDataType(ByteBuffer buff, boolean nested) {
            int length = DataUtils.readVarInt(buff);
            if(length < 0 || length > MAX_DATA_TYPE_NAME_LENGTH) {
                throw new IllegalArgumentException("Invalid data type name length " + length);
            }

            String className = DataUtils.readString(buff, length);
            if(!VALID_STORE_DATA_TYPES.contains(className) || (nested && className.equals(H2_VERSIONED_VALUE_TYPE))) {
                throw new IllegalArgumentException("Unexpected data type " + className);
            }

            if(className.equals(H2_ROW_DATA_TYPE)) {
                DataUtils.readVarInt(buff);
                skipIntArray(buff);
                skipIntArray(buff);
                buff.get();
            } else if(className.equals(H2_VALUE_DATA_TYPE)) {
                skipIntArray(buff);
                DataUtils.readVarInt(buff);
                skipIntArray(buff);
                buff.get();
            } else if(className.equals(H2_VERSIONED_VALUE_TYPE)) {
                readDataType(buff, true);
            }

            return className;
        }

        private static void skipIntArray(ByteBuffer buff) {
            for(int i = DataUtils.readVarInt(buff) - 1; i > 0; i--) {
                DataUtils.readVarInt(buff);
            }
        }

        @Override
        public int getMemory(String obj) {
            return 0;
        }

        @Override
        public void write(WriteBuffer buff, String obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] createStorage(int size) {
            return new String[size];
        }
    }

    private static class NoDeserializationSerializer implements JavaObjectSerializer {
        @Override
        public byte[] serialize(Object obj) {
            throw new UnsupportedOperationException("Serialization of Java objects is not supported in wallet files.");
        }

        @Override
        public Object deserialize(byte[] bytes) {
            throw new UnsupportedOperationException("Deserialization of Java objects is not supported in wallet files.");
        }
    }
}
