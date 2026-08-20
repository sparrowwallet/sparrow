package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.Argon2KeyDeriver;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Comparator;

public class DbPersistenceTest {
    private Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("sprw-persistence");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(tempDir != null) {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
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
        Assertions.assertThrows(StorageException.class, storage::loadUnencryptedWallet);
    }

    @Test
    public void linkedTableRejectedWithoutExecuting() throws Exception {
        File marker = tempDir.resolve("output.csv").toFile();
        String target = "jdbc:h2:" + tempDir.resolve("external") + ";INIT=CREATE TABLE IF NOT EXISTS PUB(ID INT)\\;CALL CSVWRITE('" + marker.getAbsolutePath() + "','SELECT 1')";
        File walletFile = buildWalletFile("create force linked table wallet_master.remote('','" + target.replace("'", "''") + "','sa','','PUB')");
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
        Assertions.assertTrue(new Storage(PersistenceType.DB, siblingFile).loadUnencryptedWallet().getWallet().isValid());
        Assertions.assertTrue(new Storage(PersistenceType.DB, storage.getWalletFile()).loadEncryptedWallet("pass").getWallet().isValid());

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
        Assertions.assertTrue(new Storage(PersistenceType.DB, storage.getWalletFile()).loadEncryptedWallet("pass").getWallet().isValid());
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
        Assertions.assertTrue(new Storage(PersistenceType.DB, storage.getWalletFile()).loadUnencryptedWallet().getWallet().isValid());
        Assertions.assertEquals(otherHash, getFileHash(otherFile), "another wallet file was written by a wallet name containing the extension");
    }

    @Test
    public void passwordRemovalDecryptsWalletFile() throws Exception {
        Storage storage = createUnencryptedWallet("Savings");
        setPassword(storage, "pass");
        setPassword(storage, null);
        storage.closeAndWait();

        Assertions.assertTrue(new Storage(PersistenceType.DB, storage.getWalletFile()).loadUnencryptedWallet().getWallet().isValid());
    }
}
