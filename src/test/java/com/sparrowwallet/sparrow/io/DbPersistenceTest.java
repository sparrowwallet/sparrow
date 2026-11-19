package com.sparrowwallet.sparrow.io;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.crypto.Argon2KeyDeriver;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletBlockHeightChangedEvent;
import com.sparrowwallet.sparrow.event.WalletEntryLabelsChangedEvent;
import com.sparrowwallet.sparrow.event.WalletPasswordChangeFailedEvent;
import com.sparrowwallet.sparrow.io.db.DbPersistence;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DbPersistenceTest {
    private Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("sprw-persistence");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(tempDir != null) {
            try(Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private File buildWalletFile(String... schemaObjects) throws Exception {
        String base = tempDir.resolve("wallet").toString();
        try(Connection connection = DriverManager.getConnection("jdbc:h2:" + base + ";DATABASE_TO_UPPER=false;DB_CLOSE_ON_EXIT=FALSE", "sa", ""); Statement statement = connection.createStatement()) {
            statement.execute("create schema wallet_master");
            statement.execute("create table wallet_master.wallet(id identity not null, name varchar(255) not null)");
            for(String ddl : schemaObjects) {
                statement.execute(ddl);
            }
        }
        return new File(base + ".mv.db");
    }

    private void assertRejected(File walletFile) {
        Storage storage = new Storage(PersistenceType.DB, walletFile);
        try {
            Assertions.assertThrows(StorageException.class, storage::loadUnencryptedWallet);
        } finally {
            storage.closeAndWait();
        }
    }

    @Test
    public void linkedTableRejectedWithoutExecuting() throws Exception {
        File marker = tempDir.resolve("output.csv").toFile();
        //H2 treats a backslash in a URL setting as an escape, so a Windows marker path must use forward slashes
        String target = "jdbc:h2:" + tempDir.resolve("external") + ";INIT=CREATE TABLE IF NOT EXISTS PUB(ID INT)\\;CALL CSVWRITE('" + marker.getAbsolutePath().replace('\\', '/') + "','SELECT 1')";
        File walletFile = buildWalletFile("create force linked table wallet_master.remote('','" + target.replace("'", "''") + "','sa','','PUB')");
        Assertions.assertTrue(marker.exists(), "fixture did not write the marker");
        marker.delete();

        assertRejected(walletFile);
        Assertions.assertFalse(marker.exists(), "linked table connected during load");
    }

    @Test
    public void triggerRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.t(id int)",
                "create force trigger wallet_master.tg before insert on wallet_master.t for each row call \"com.sparrowwallet.Missing\""));
    }

    @Test
    public void generatedColumnRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.g(id int, computed int generated always as (id + 1))"));
    }

    @Test
    public void columnDefaultFunctionRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.d(id int, x int default LENGTH(FILE_READ('/etc/hostname')))"));
    }

    @Test
    public void constantRejected() throws Exception {
        assertRejected(buildWalletFile("create constant wallet_master.k value 1"));
    }

    @Test
    public void javaObjectColumnRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.j(id int, obj OTHER)"));
    }

    @Test
    public void checkConstraintRejected() throws Exception {
        assertRejected(buildWalletFile("create table wallet_master.c(id int check (LENGTH(FILE_READ('/etc/hostname')) > 0))"));
    }

    @Test
    public void domainRejected() throws Exception {
        assertRejected(buildWalletFile("create domain wallet_master.dm as int default 0"));
    }

    @Test
    public void walletNamePunctuation() throws Exception {
        for(String walletName : List.of("a;b", "Savings;", "Savings;ıd=1", "Savings;a\\b=1")) {
            Assertions.assertThrows(StorageException.class, () -> createUnencryptedWallet(walletName), walletName);
        }
        try(Stream<Path> paths = Files.list(tempDir)) {
            Assertions.assertEquals(0, paths.count());
        }

        Storage storage = createUnencryptedWallet("Savings (old), #2 & co.");
        storage.closeAndWait();
        Assertions.assertTrue(storage.getWalletFile().exists());
    }

    @Test
    public void analyzedWalletWithChildWalletsLoads() throws Exception {
        Storage storage = createUnencryptedWallet("Savings");
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);
        storage.saveWallet(wallet.addChildWallet(StandardAccount.ACCOUNT_1));
        Wallet namedAccount = wallet.addChildWallet(StandardAccount.ACCOUNT_2);
        namedAccount.setName("Épargne");
        storage.saveWallet(namedAccount);
        storage.closeAndWait();

        String path = storage.getWalletFile().getAbsolutePath();
        String url = "jdbc:h2:" + path.substring(0, path.length() - ".mv.db".length()) + ";DATABASE_TO_UPPER=false;DB_CLOSE_ON_EXIT=FALSE";
        try(Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE");
        }

        Storage loaded = new Storage(PersistenceType.DB, storage.getWalletFile());
        try {
            Wallet loadedWallet = loaded.loadUnencryptedWallet().getWallet();
            Assertions.assertTrue(loadedWallet.isValid());
            Assertions.assertEquals(2, loadedWallet.getChildWallets().size());
        } finally {
            loaded.closeAndWait();
        }
    }

    @Test
    public void objectInChildSchemaRejected() throws Exception {
        for(String ddl : List.of("create constant \"wallet_%s\".k value 1", "create domain \"wallet_%s\".dm as int default 0",
                "create force trigger \"wallet_%s\".tg before insert on \"wallet_%s\".\"wallet\" for each row call \"com.sparrowwallet.Missing\"",
                "create table \"wallet_%s\".g(a int, b int generated always as (a + 1))", "create table PUBLIC.g(a int, b int generated always as (a + 1))",
                "create table PUBLIC.c(a int check (a > 0))")) {
            Storage storage = createUnencryptedWallet("Savings");
            Wallet wallet = createWallet("Savings");
            storage.saveWallet(wallet);
            Wallet child = wallet.addChildWallet(StandardAccount.ACCOUNT_1);
            storage.saveWallet(child);
            storage.closeAndWait();

            String path = storage.getWalletFile().getAbsolutePath();
            String url = "jdbc:h2:" + path.substring(0, path.length() - ".mv.db".length()) + ";DATABASE_TO_UPPER=false;DB_CLOSE_ON_EXIT=FALSE";
            try(Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement()) {
                statement.execute(ddl.replace("%s", child.getName()));
            }

            assertRejected(storage.getWalletFile());
            Files.delete(storage.getWalletFile().toPath());
        }
    }

    private static final String TEST_XPUB = "xpub6BrhGFTWPd3DXo8s2BPxHHzCmBCyj8QvamcEUaq8EDwnwXpvvcU9LzpJqENHcqHkqwTn2vPhynGVoEqj3PAB3NxnYZrvCsSfoCniJKaggdy";

    private Wallet createWallet(String walletName) {
        Wallet wallet = new Wallet(walletName);
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);

        Keystore keystore = new Keystore("Keystore 1");
        keystore.setSource(KeystoreSource.SW_WATCH);
        keystore.setWalletModel(WalletModel.SPARROW);
        keystore.setKeyDerivation(new KeyDerivation("60bcd3a7", "m/84'/0'/3'"));
        keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(TEST_XPUB));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), null));

        return wallet;
    }

    private Storage createUnencryptedWallet(String walletName) throws Exception {
        Storage storage = new Storage(PersistenceType.DB, tempDir.resolve(walletName + "." + PersistenceType.DB.getExtension()).toFile());
        storage.setKeyDeriver(new Argon2KeyDeriver());
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        storage.saveWallet(createWallet(walletName));

        return storage;
    }

    private void setPassword(Storage storage, CharSequence password) throws Exception {
        ECKey encryptionPubKey = password == null ? Storage.NO_PASSWORD_KEY : ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey(password));
        storage.setEncryptionPubKey(encryptionPubKey);
        storage.saveWallet(createWallet(storage.getWalletName(null)));
    }

    private Sha256Hash getFileHash(File file) throws Exception {
        return Sha256Hash.of(Files.readAllBytes(file.toPath()));
    }

    private boolean isWalletValid(File walletFile, CharSequence password) throws Exception {
        Storage storage = new Storage(PersistenceType.DB, walletFile);
        try {
            return (password == null ? storage.loadUnencryptedWallet() : storage.loadEncryptedWallet(password)).getWallet().isValid();
        } finally {
            storage.closeAndWait();
        }
    }

    @Test
    public void passwordChangeLeavesSiblingWalletUntouched() throws Exception {
        Storage siblingStorage = createUnencryptedWallet("Savings.old");
        siblingStorage.closeAndWait();
        File siblingFile = siblingStorage.getWalletFile();
        Sha256Hash siblingHash = getFileHash(siblingFile);

        Storage storage = createUnencryptedWallet("Savings");
        setPassword(storage, "pass");
        storage.closeAndWait();

        Assertions.assertEquals(siblingHash, getFileHash(siblingFile), "sibling wallet file was rewritten by the password change");
        Assertions.assertTrue(isWalletValid(siblingFile, null));
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), "pass"));

        //The conversion must not leave the wallet copy or H2's temp.db behind in the wallets directory
        String[] tempFiles = tempDir.toFile().list((dir, name) -> name.equals("temp.db") || name.startsWith("sparrowenc"));
        Assertions.assertEquals(0, tempFiles == null ? -1 : tempFiles.length, "temporary files were left in the wallets directory");
    }

    @Test
    public void passwordChangeAppliesToWalletNameContainingDot() throws Exception {
        Storage siblingStorage = createUnencryptedWallet("Savings");
        siblingStorage.closeAndWait();
        Sha256Hash siblingHash = getFileHash(siblingStorage.getWalletFile());

        Storage storage = createUnencryptedWallet("Savings.old");
        setPassword(storage, "pass");
        storage.closeAndWait();

        Assertions.assertEquals(siblingHash, getFileHash(siblingStorage.getWalletFile()));
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), "pass"));
    }

    @Test
    public void walletNameContainingExtensionUsesItsOwnFile() throws Exception {
        Storage otherStorage = createUnencryptedWallet("backup2");
        otherStorage.closeAndWait();
        File otherFile = otherStorage.getWalletFile();
        Sha256Hash otherHash = getFileHash(otherFile);

        //The file for this wallet is backup.mv.db2.mv.db - removing every occurrence of the extension from the path yields the database name backup2
        Storage storage = createUnencryptedWallet("backup.mv.db2");
        storage.closeAndWait();

        Assertions.assertTrue(storage.getWalletFile().exists(), "wallet was written to a file other than the one tracked");
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), null));
        Assertions.assertEquals(otherHash, getFileHash(otherFile), "another wallet file was written by a wallet name containing the extension");
    }

    @Test
    public void newWalletFileWithoutExtensionSaves() throws Exception {
        Storage storage = new Storage(tempDir.resolve("newwallet").toFile());
        storage.setKeyDeriver(new Argon2KeyDeriver());
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        storage.saveWallet(createWallet("newwallet"));
        storage.closeAndWait();

        File walletFile = tempDir.resolve("newwallet." + PersistenceType.DB.getExtension()).toFile();
        Assertions.assertTrue(walletFile.exists());
        Assertions.assertTrue(isWalletValid(walletFile, null));
    }

    @Test
    public void walletFileWithEmptyNameRejected() throws Exception {
        File dir = tempDir.resolve("wallet").toFile();
        Assertions.assertTrue(dir.mkdir());
        File emptyNameFile = new File(dir, "." + PersistenceType.DB.getExtension());
        File cleanFile = buildWalletFile();
        Files.move(cleanFile.toPath(), emptyNameFile.toPath());

        File marker = tempDir.resolve("output.csv").toFile();
        String target = "jdbc:h2:" + tempDir.resolve("external") + ";INIT=CREATE TABLE IF NOT EXISTS PUB(ID INT)\\;CALL CSVWRITE('" + marker.getAbsolutePath().replace('\\', '/') + "','SELECT 1')";
        buildWalletFile("create force linked table wallet_master.remote('','" + target.replace("'", "''") + "','sa','','PUB')");
        marker.delete();

        assertRejected(emptyNameFile);
        Assertions.assertFalse(marker.exists());
    }

    /**
     * An update is queued with the key the storage held at the time, and can still be waiting when the wallet is saved with a password added. Run
     * after that save, it must leave the file as the save left it.
     */
    @Test
    public void updateQueuedBeforeAPasswordWasAddedLeavesTheFileEncrypted() throws Exception {
        Persistence persistence = PersistenceType.DB.getInstance();
        Storage storage = new Storage(persistence, tempDir.resolve("Savings." + PersistenceType.DB.getExtension()).toFile());
        storage.setKeyDeriver(new Argon2KeyDeriver());
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);

        setPassword(storage, "pass");
        persistence.updateWallet(storage, wallet);      //as it was queued, with no key
        storage.closeAndWait();

        Assertions.assertTrue(Storage.isEncrypted(storage.getWalletFile()));
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), "pass"));
    }

    /**
     * The same in the other direction: an update queued while the wallet had a password does not put it back once it has been removed.
     */
    @Test
    public void updateQueuedBeforeAPasswordWasRemovedLeavesTheFileUnencrypted() throws Exception {
        Persistence persistence = PersistenceType.DB.getInstance();
        Storage storage = new Storage(persistence, tempDir.resolve("Savings." + PersistenceType.DB.getExtension()).toFile());
        storage.setKeyDeriver(new Argon2KeyDeriver());
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);
        setPassword(storage, "pass");
        ECKey queuedKey = storage.getEncryptionPubKey();

        setPassword(storage, null);
        persistence.updateWallet(storage, wallet, queuedKey);
        storage.closeAndWait();

        Assertions.assertFalse(Storage.isEncrypted(storage.getWalletFile()));
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), null));
    }

    /**
     * The path that must keep working: a password added without a full save reaches the file through the update queued with the new key.
     */
    @Test
    public void updateQueuedWithANewPasswordEncryptsTheFile() throws Exception {
        Storage storage = createUnencryptedWallet("Savings");
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);

        storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")));
        storage.updateWallet(wallet);
        storage.closeAndWait();

        Assertions.assertTrue(Storage.isEncrypted(storage.getWalletFile()));
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), "pass"));
    }

    /**
     * Backups requested deleted with a password change go once the update queued with the new key has written the file.
     */
    @Test
    public void updateQueuedWithANewPasswordDeletesBackupsOnceWritten() throws Exception {
        AtomicInteger backupDeletions = new AtomicInteger();
        Storage storage = createBackupCountingStorage("Savings", backupDeletions);
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);

        storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")), true);
        Assertions.assertEquals(0, backupDeletions.get());
        storage.updateWallet(wallet);
        storage.updateWallet(wallet);
        storage.closeAndWait();

        Assertions.assertEquals(1, backupDeletions.get());
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), "pass"));
    }

    /**
     * A write that started before the password was last changed has not written the file with the new key, so it leaves the backups requested deleted.
     */
    @Test
    public void backupsAreDeletedOnlyForTheKeyTheyWereRequestedWith() throws Exception {
        AtomicInteger backupDeletions = new AtomicInteger();
        Storage storage = createBackupCountingStorage("Savings", backupDeletions);
        ECKey previousKey = ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("previous"));
        ECKey requestedKey = ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass"));

        storage.setEncryptionPubKey(requestedKey, true);
        storage.encryptionKeySaved(previousKey);
        storage.encryptionKeySaved(Storage.NO_PASSWORD_KEY);
        Assertions.assertEquals(0, backupDeletions.get());

        storage.encryptionKeySaved(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")));
        storage.encryptionKeySaved(requestedKey);
        Assertions.assertEquals(1, backupDeletions.get());
    }

    /**
     * A save confirming the password while a change to it is still to reach the file keeps the deletion requested with the change, and a change to
     * another password without deleting backups withdraws it.
     */
    @Test
    public void backupDeletionSurvivesASaveKeepingThePassword() throws Exception {
        AtomicInteger backupDeletions = new AtomicInteger();
        Storage storage = createBackupCountingStorage("Savings", backupDeletions);
        ECKey requestedKey = ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass"));

        storage.setEncryptionPubKey(requestedKey, true);
        storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")), false);
        storage.encryptionKeySaved(requestedKey);
        Assertions.assertEquals(1, backupDeletions.get());

        ECKey otherKey = ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("other"));
        storage.setEncryptionPubKey(requestedKey, true);
        storage.setEncryptionPubKey(otherKey, false);
        storage.encryptionKeySaved(requestedKey);
        storage.encryptionKeySaved(otherKey);
        Assertions.assertEquals(1, backupDeletions.get());
    }

    /**
     * A password change that fails on the update thread is reported once, however many updates go on to try it, and leaves the file and its backups as
     * they were.
     */
    @Test
    public void updateFailingToChangeThePasswordIsReportedAndKeepsBackups() throws Throwable {
        AtomicInteger backupDeletions = new AtomicInteger();
        Storage storage = createBackupCountingStorage("Savings", backupDeletions);
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);

        List<WalletPasswordChangeFailedEvent> events = failPasswordChange(() -> {
            storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")), true);
            storage.updateWallet(wallet);
            storage.updateWallet(wallet);
            storage.closeAndWait();
        });

        Assertions.assertEquals(1, events.size());
        Assertions.assertSame(wallet, events.getFirst().getWallet());
        Assertions.assertEquals(0, backupDeletions.get());
        Assertions.assertFalse(Storage.isEncrypted(storage.getWalletFile()));
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), null));
    }

    /**
     * A full save that fails to change the password throws to its caller, which reports it, so the updates that go on to retry the change do not.
     */
    @Test
    public void fullSaveFailingToChangeThePasswordIsNotReportedAgain() throws Throwable {
        AtomicInteger backupDeletions = new AtomicInteger();
        Storage storage = createBackupCountingStorage("Savings", backupDeletions);
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);

        List<WalletPasswordChangeFailedEvent> events = failPasswordChange(() -> {
            storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")), true);
            Assertions.assertThrows(StorageException.class, () -> storage.saveWallet(wallet));
            storage.updateWallet(wallet);
            storage.closeAndWait();
        });

        Assertions.assertEquals(0, events.size());
        Assertions.assertEquals(0, backupDeletions.get());
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), null));
    }

    /**
     * The same for a full save removing the password.
     */
    @Test
    public void fullSaveFailingToRemoveThePasswordIsNotReportedAgain() throws Throwable {
        AtomicInteger backupDeletions = new AtomicInteger();
        Storage storage = createBackupCountingStorage("Savings", backupDeletions);
        Wallet wallet = createWallet("Savings");
        storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")));
        storage.saveWallet(wallet);

        List<WalletPasswordChangeFailedEvent> events = failPasswordChange(() -> {
            storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY, true);
            Assertions.assertThrows(StorageException.class, () -> storage.saveWallet(wallet));
            storage.updateWallet(wallet);
            storage.closeAndWait();
        });

        Assertions.assertEquals(0, events.size());
        Assertions.assertEquals(0, backupDeletions.get());
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), "pass"));
    }

    /**
     * Removing a password writes the wallet before the file, so a removal failing on the update thread is reported as one, and the data still saved.
     */
    @Test
    public void updateFailingToRemoveThePasswordIsReportedAsARemoval() throws Throwable {
        AtomicInteger backupDeletions = new AtomicInteger();
        Storage storage = createBackupCountingStorage("Savings", backupDeletions);
        Wallet wallet = createWallet("Savings");
        storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass")));
        storage.saveWallet(wallet);

        List<WalletPasswordChangeFailedEvent> events = failPasswordChange(() -> {
            storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY, true);
            EventManager.get().post(new WalletBlockHeightChangedEvent(wallet, 850000));
            storage.updateWallet(wallet);
            storage.closeAndWait();
        });

        Assertions.assertEquals(1, events.size());
        Assertions.assertTrue(events.getFirst().isPasswordRemoved());
        Assertions.assertEquals(0, backupDeletions.get());

        Storage savedStorage = new Storage(PersistenceType.DB, storage.getWalletFile());
        try {
            Assertions.assertEquals(850000, savedStorage.loadEncryptedWallet("pass").getWallet().getStoredBlockHeight());
        } finally {
            savedStorage.closeAndWait();
        }
    }

    /**
     * A full save that succeeds with another password clears a failure recorded by an earlier one, so the same password failing again is reported.
     */
    @Test
    public void passwordFailingAgainAfterASuccessfulSaveIsReported() throws Throwable {
        Storage storage = createBackupCountingStorage("Savings", new AtomicInteger());
        Wallet wallet = createWallet("Savings");
        storage.saveWallet(wallet);
        ECKey failingKey = ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("pass"));

        List<WalletPasswordChangeFailedEvent> events = failPasswordChange(() -> {
            storage.setEncryptionPubKey(failingKey);
            Assertions.assertThrows(StorageException.class, () -> storage.saveWallet(wallet));
        });
        Assertions.assertEquals(0, events.size());

        storage.setEncryptionPubKey(ECKey.fromPublicOnly(storage.getKeyDeriver().deriveECKey("other")));
        storage.saveWallet(wallet);

        events = failPasswordChange(() -> {
            storage.setEncryptionPubKey(failingKey);
            storage.updateWallet(wallet);
            storage.closeAndWait();
        });

        Assertions.assertEquals(1, events.size());
        Assertions.assertFalse(events.getFirst().isPasswordRemoved());
        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), "other"));
    }

    /**
     * Runs the password change with the wallet folder unable to take new entries, which the new file is written as. Updates queued by the change
     * run when the storage is closed, which must be done within it.
     */
    private List<WalletPasswordChangeFailedEvent> failPasswordChange(Executable passwordChange) throws Throwable {
        List<WalletPasswordChangeFailedEvent> events = new CopyOnWriteArrayList<>();
        Object subscriber = new Object() {
            @Subscribe
            public void walletPasswordChangeFailed(WalletPasswordChangeFailedEvent event) {
                events.add(event);
            }
        };

        Set<PosixFilePermission> permissions;
        try {
            permissions = Files.getPosixFilePermissions(tempDir);
        } catch(UnsupportedOperationException e) {
            throw Assumptions.<RuntimeException>abort("POSIX permissions are not supported: " + e.getMessage());
        }

        EventManager.get().register(subscriber);
        Files.setPosixFilePermissions(tempDir, PosixFilePermissions.fromString("r-x------"));
        try {
            Assumptions.assumeFalse(Files.isWritable(tempDir), "folder permissions are not enforced for this user");
            passwordChange.execute();
        } finally {
            Files.setPosixFilePermissions(tempDir, permissions);
            EventManager.get().unregister(subscriber);
        }

        return events;
    }

    private Storage createBackupCountingStorage(String walletName, AtomicInteger backupDeletions) {
        Storage storage = new Storage(PersistenceType.DB, tempDir.resolve(walletName + "." + PersistenceType.DB.getExtension()).toFile()) {
            @Override
            public void deleteBackups() {
                backupDeletions.incrementAndGet();
            }
        };
        storage.setKeyDeriver(new Argon2KeyDeriver());
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);

        return storage;
    }

    @Test
    public void passwordChangeKeepsSymlinkedWalletInPlace() throws Exception {
        Storage targetStorage = createUnencryptedWallet("Volume");
        targetStorage.closeAndWait();
        Path walletsDir = Files.createDirectory(tempDir.resolve("wallets"));
        Path link;
        try {
            link = Files.createSymbolicLink(walletsDir.resolve("Savings." + PersistenceType.DB.getExtension()), targetStorage.getWalletFile().toPath());
        } catch(UnsupportedOperationException | IOException e) {
            //Windows only allows symbolic links to be created in Developer Mode or when elevated
            throw Assumptions.<RuntimeException>abort("Symbolic links cannot be created: " + e.getMessage());
        }

        Storage storage = new Storage(PersistenceType.DB, link.toFile());
        storage.setKeyDeriver(new Argon2KeyDeriver());
        storage.loadUnencryptedWallet();
        setPassword(storage, "pass");
        storage.closeAndWait();

        Assertions.assertTrue(Files.isSymbolicLink(link), "the symlink was replaced by the wallet file");
        Assertions.assertTrue(isWalletValid(targetStorage.getWalletFile(), "pass"));
        String[] walletsDirFiles = walletsDir.toFile().list();
        Assertions.assertEquals(1, walletsDirFiles == null ? -1 : walletsDirFiles.length, "files were left in the wallets directory");
        String[] tempFiles = tempDir.toFile().list((dir, name) -> name.equals("temp.db") || name.startsWith("sparrowenc"));
        Assertions.assertEquals(0, tempFiles == null ? -1 : tempFiles.length, "temporary files were left alongside the wallet file");
    }

    @Test
    public void longAddressLabelDoesNotBlockLaterUpdates() throws Exception {
        Persistence persistence = PersistenceType.DB.getInstance();
        Storage storage = new Storage(persistence, tempDir.resolve("Labels." + PersistenceType.DB.getExtension()).toFile());
        storage.setKeyDeriver(new Argon2KeyDeriver());
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        Wallet wallet = createWallet("Labels");
        storage.saveWallet(wallet);

        WalletNode firstNode = new WalletNode(wallet, KeyPurpose.RECEIVE, 0);
        firstNode.setLabel("a".repeat(300));
        wallet.getNode(KeyPurpose.RECEIVE).getChildren().add(firstNode);
        ((DbPersistence)persistence).walletEntryLabelsChanged(new WalletEntryLabelsChangedEvent(wallet, new NodeEntry(wallet, firstNode, false)));
        persistence.updateWallet(storage, wallet);

        WalletNode secondNode = new WalletNode(wallet, KeyPurpose.RECEIVE, 1);
        secondNode.setLabel("Second");
        wallet.getNode(KeyPurpose.RECEIVE).getChildren().add(secondNode);
        ((DbPersistence)persistence).walletEntryLabelsChanged(new WalletEntryLabelsChangedEvent(wallet, new NodeEntry(wallet, secondNode, false)));
        persistence.updateWallet(storage, wallet);
        storage.closeAndWait();

        Storage reopened = new Storage(PersistenceType.DB, storage.getWalletFile());
        try {
            Wallet loaded = reopened.loadUnencryptedWallet().getWallet();
            Map<Integer, String> labels = loaded.getNode(KeyPurpose.RECEIVE).getChildren().stream().filter(node -> node.getLabel() != null)
                    .collect(Collectors.toMap(WalletNode::getIndex, WalletNode::getLabel));
            Assertions.assertEquals("a".repeat(255), labels.get(0));
            Assertions.assertEquals("Second", labels.get(1));
        } finally {
            reopened.closeAndWait();
        }
    }

    @Test
    public void passwordRemovalDecryptsWalletFile() throws Exception {
        Storage storage = createUnencryptedWallet("Savings");
        setPassword(storage, "pass");
        setPassword(storage, null);
        storage.closeAndWait();

        Assertions.assertTrue(isWalletValid(storage.getWalletFile(), null));
    }
}
