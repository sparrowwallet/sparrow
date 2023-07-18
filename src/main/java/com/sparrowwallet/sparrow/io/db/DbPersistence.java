package com.sparrowwallet.sparrow.io.db;

import com.google.common.eventbus.Subscribe;
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
import org.h2.tools.ChangeFileEncryption;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
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

        migrate(storage, MASTER_SCHEMA, encryptionKey);

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
    }

    private Map<WalletAndKey, Storage> loadChildWallets(Storage storage, Wallet masterWallet, ECKey encryptionKey) throws StorageException {
        Jdbi jdbi = getJdbi(storage, getFilePassword(encryptionKey));
        List<String> schemas = jdbi.withHandle(handle -> {
           return handle.createQuery("show schemas").mapTo(String.class).list();
        });

        List<String> childSchemas = schemas.stream().filter(schema -> schema.startsWith(WALLET_SCHEMA_PREFIX) && !schema.equals(MASTER_SCHEMA)).collect(Collectors.toList());
        Map<WalletAndKey, Storage> childWallets = new TreeMap<>();
        for(String schema : childSchemas) {
            migrate(storage, schema, encryptionKey);

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
        updatePassword(storage, encryptionPubKey);

        updateExecutor.execute(() -> {
            try {
                update(storage, wallet, getFilePassword(encryptionPubKey));
            } catch(Exception e) {
                log.error("Error updating wallet db", e);
            }
        });
    }

    private synchronized void createUpdateExecutor(Wallet masterWallet) {
        if(updateExecutor == null) {
            BasicThreadFactory factory = new BasicThreadFactory.Builder().namingPattern(masterWallet.getFullName() + "-dbupdater").daemon(true).priority(Thread.NORM_PRIORITY).build();
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
                    handle.execute("drop schema `" + getSchema(wallet) + "` cascade");
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
                                long purposeNodeId = walletNodeDao.insertWalletNode(purposeNode.getDerivationPath(), purposeNode.getLabel(), wallet.getId(), null, null);
                                purposeNode.setId(purposeNodeId);
                            }

                            long nodeId = walletNodeDao.insertWalletNode(addressNode.getDerivationPath(), addressNode.getLabel(), wallet.getId(), purposeNode.getId(), addressNode.getAddressData());
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
                        blockTransactionDao.addOrUpdate(wallet, txid, blkTx);
                    }
                    if(!dirtyPersistables.clearHistory) {
                        DetachedLabelDao detachedLabelDao = handle.attach(DetachedLabelDao.class);
                        detachedLabelDao.clearAndAddAll(wallet);
                    }
                }

                if(dirtyPersistables.label != null) {
                    walletDao.updateLabel(wallet.getId(), dirtyPersistables.label.length() > 255 ? dirtyPersistables.label.substring(0, 255) : dirtyPersistables.label);
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
                                    long purposeNodeId = walletNodeDao.insertWalletNode(purposeNode.getDerivationPath(), purposeNode.getLabel(), wallet.getId(), null, null);
                                    purposeNode.setId(purposeNodeId);
                                }

                                long nodeId = walletNodeDao.insertWalletNode(addressNode.getDerivationPath(), addressNode.getLabel(), wallet.getId(), purposeNode.getId(), addressNode.getAddressData());
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

    private void updatePassword(Storage storage, ECKey encryptionPubKey) {
        String newPassword = getFilePassword(encryptionPubKey);
        String currentPassword = getDatasourcePassword();

        //The password only needs to be changed if the datasource is not null - if we have not loaded the wallet from a datasource, it is a new wallet and the database is still to be created
        if(dataSource != null && !Objects.equals(currentPassword, newPassword)) {
            if(!dataSource.isClosed()) {
                dataSource.close();
            }

            try {
                File walletFile = storage.getWalletFile();
                ChangeFileEncryption.execute(walletFile.getParent(), getWalletName(walletFile, null), "AES",
                        currentPassword == null ? null : currentPassword.toCharArray(),
                        newPassword == null ? null : newPassword.toCharArray(), true);

                if(newPassword != null) {
                    writeBinaryHeader(walletFile);
                }

                //This sets the new password on the datasource for the next updatePassword check
                getDataSource(storage, newPassword);
            } catch(Exception e) {
                log.error("Error changing database password", e);
            }
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
        return Flyway.configure().dataSource(getDataSource(storage, password)).locations("filesystem:" + resourcesDir.getAbsolutePath()).schemas(schema).failOnMissingLocations(true).load();
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

    private String getUrl(File walletFile, String password) {
        return "jdbc:h2:" + walletFile.getAbsolutePath().replace("." + getType().getExtension(), "") + ";INIT=SET TRACE_LEVEL_FILE=4;TRACE_LEVEL_FILE=4;DEFRAG_ALWAYS=true;MAX_COMPACT_TIME=5000;DATABASE_TO_UPPER=false" + (password == null ? "" : ";CIPHER=AES");
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
    public void walletMixConfigChanged(WalletMixConfigChangedEvent event) {
        if(persistsFor(event.getWallet()) && event.getWallet().getMixConfig() != null) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).mixConfig = true);
        }
    }

    @Subscribe
    public void walletUtxoMixesChanged(WalletUtxoMixesChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> {
                dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).changedUtxoMixes.putAll(event.getChangedUtxoMixes());
                dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).removedUtxoMixes.putAll(event.getRemovedUtxoMixes());
            });
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
    public void walletWatchLastChanged(WalletWatchLastChangedEvent event) {
        if(persistsFor(event.getWallet())) {
            updateExecutor.execute(() -> dirtyPersistablesMap.computeIfAbsent(event.getWallet(), key -> new DirtyPersistables()).watchLast = event.getWatchLast());
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
        public boolean mixConfig;
        public final Map<Sha256Hash, UtxoMixData> changedUtxoMixes = new HashMap<>();
        public final Map<Sha256Hash, UtxoMixData> removedUtxoMixes = new HashMap<>();
        public final List<Keystore> labelKeystores = new ArrayList<>();
        public final List<Keystore> encryptionKeystores = new ArrayList<>();

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
                    "\nMix config:" + mixConfig +
                    "\nUTXO mixes changed:" + changedUtxoMixes +
                    "\nUTXO mixes removed:" + removedUtxoMixes +
                    "\nKeystore labels:" + labelKeystores.stream().map(Keystore::getLabel).collect(Collectors.toList()) +
                    "\nKeystore encryptions:" + encryptionKeystores.stream().map(Keystore::getLabel).collect(Collectors.toList());
        }
    }
}
