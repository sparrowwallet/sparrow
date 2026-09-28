package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

public class StorageTest extends IoTest {
    private static final String EXISTING_MNEMONIC = "response seminar brave tip suit recall often sound stick owner lottery motion";
    private static final String OPENED_MNEMONIC = "aware report movie exile buyer drum poverty supreme gym oppose float elegant";
    private static final String DERIVATION = "m/84'/0'/0'";

    private static String existingXpub;
    private static String openedXpub;

    private Path tempDir;

    @BeforeAll
    static void deriveXpubs() throws Exception {
        existingXpub = seedXpub(EXISTING_MNEMONIC);
        openedXpub = seedXpub(OPENED_MNEMONIC);
    }

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("sparrow-storage");
    }

    @Test
    public void loadWallet() throws IOException, MnemonicException, StorageException {
        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_NETWORKS_PROPERTY, "true");
        Storage storage = new Storage(getFile("sparrow-single-wallet"));
        Wallet wallet = storage.loadEncryptedWallet("pass").getWallet();
        Assertions.assertTrue(wallet.isValid());
    }

    @Test
    public void loadSeedWallet() throws IOException, MnemonicException, StorageException {
        Storage storage = new Storage(getFile("sparrow-single-seed-wallet"));
        WalletAndKey walletAndKey = storage.loadEncryptedWallet("pass");
        Wallet wallet = walletAndKey.getWallet();
        Wallet copy = wallet.copy();
        copy.decrypt(walletAndKey.getKey());

        for(int i = 0; i < wallet.getKeystores().size(); i++) {
            Keystore keystore = wallet.getKeystores().get(i);
            if(keystore.hasSeed()) {
                Keystore copyKeystore = copy.getKeystores().get(i);
                Keystore derivedKeystore = Keystore.fromSeed(copyKeystore.getSeed(), wallet.getPolicyType(), copyKeystore.getKeyDerivation().getDerivation());
                keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                keystore.getSeed().setPassphrase(copyKeystore.getSeed().getPassphrase());
                copyKeystore.getSeed().clear();
            }
        }

        Assertions.assertTrue(wallet.isValid());

        Assertions.assertTrue(wallet.getName().startsWith("sparrow-single-seed-wallet"));
        Assertions.assertEquals(PolicyType.SINGLE_HD, wallet.getPolicyType());
        Assertions.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Assertions.assertEquals(1, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assertions.assertEquals("pkh(60bcd3a7)", wallet.getDefaultPolicy().getMiniscript().getScript());
        Assertions.assertEquals("60bcd3a7", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals("m/84'/0'/3'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("xpub6BrhGFTWPd3DXo8s2BPxHHzCmBCyj8QvamcEUaq8EDwnwXpvvcU9LzpJqENHcqHkqwTn2vPhynGVoEqj3PAB3NxnYZrvCsSfoCniJKaggdy", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
        Assertions.assertEquals("af6ebd81714c301c3a71fe11a7a9c99ccef4b33d4b36582220767bfa92768a2aa040f88b015b2465f8075a8b9dbf892a7d6e6c49932109f2cbc05ba0bd7f355fbcc34c237f71be5fb4dd7f8184e44cb0", Utils.bytesToHex(wallet.getKeystores().get(0).getSeed().getEncryptedData().getEncryptedBytes()));
        Assertions.assertNull(wallet.getKeystores().get(0).getSeed().getMnemonicCode());
        Assertions.assertEquals("bc1q2mkrttcuzryrdyn9vtu3nfnt3jlngwn476ktus", wallet.getFreshNode(KeyPurpose.RECEIVE).getAddress().toString());
    }

    @Test
    public void multipleLoadTest() throws IOException, MnemonicException, StorageException {
        for(int i = 0; i < 5; i++) {
            loadSeedWallet();
        }
    }

    @Test
    public void saveWallet() throws IOException, MnemonicException, StorageException {
        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_NETWORKS_PROPERTY, "true");
        Storage storage = new Storage(getFile("sparrow-single-wallet"));
        Wallet wallet = storage.loadEncryptedWallet("pass").getWallet();
        Assertions.assertTrue(wallet.isValid());

        File tempWallet = File.createTempFile("sparrow", "tmp");
        tempWallet.deleteOnExit();

        Storage tempStorage = new Storage(tempWallet);
        tempStorage.setKeyDeriver(storage.getKeyDeriver());
        tempStorage.setEncryptionPubKey(storage.getEncryptionPubKey());
        tempStorage.saveWallet(wallet);

        Storage temp2Storage = new Storage(tempWallet);
        wallet = temp2Storage.loadEncryptedWallet("pass").getWallet();
        Assertions.assertTrue(wallet.isValid());
    }

    @Test
    public void getBackupsExcludesLongerWalletNames() throws IOException {
        File backupDir = createBackupDir("Savings-20250101120000.mv.db", "Savings-20240101120000.mv.db",
                "Savings-2023-20250101120000.mv.db", "Savings.old-20250101120000.mv.db", "SavingsX-20250101120000.mv.db");

        assertBackups(backupDir, PersistenceType.DB, "Savings.mv.db", "Savings-20250101120000.mv.db", "Savings-20240101120000.mv.db");
        assertBackups(backupDir, PersistenceType.DB, "Savings-2023.mv.db", "Savings-2023-20250101120000.mv.db");
        assertBackups(backupDir, PersistenceType.DB, "Savings.old.mv.db", "Savings.old-20250101120000.mv.db");
    }

    @Test
    public void getBackupsRequiresAWholeDateAndAMatchingExtension() throws IOException {
        File backupDir = createBackupDir("Savings-20250101120000.mv.db", "Savings-2025010112000.mv.db", "Savings-202501011200000.mv.db",
                "Savings-20250101120000.json", "Savings-20250101120000", "Savings.mv.db", "Savings-notes.txt");

        assertBackups(backupDir, PersistenceType.DB, "Savings.mv.db", "Savings-20250101120000.mv.db");
        assertBackups(backupDir, PersistenceType.JSON, "Savings.json", "Savings-20250101120000.json");
        assertBackups(backupDir, PersistenceType.JSON, "Savings", "Savings-20250101120000");
    }

    @Test
    public void getBackupsTreatsAWalletNameLiterally() throws IOException {
        File backupDir = createBackupDir("SavingsXold-20250101120000.mv.db");

        assertBackups(backupDir, PersistenceType.DB, "Savings.old.mv.db");
    }

    @Test
    public void migrateNamesWalletAfterOpenedFile() throws Exception {
        File walletsDir = Files.createDirectory(tempDir.resolve("wallets")).toFile();
        File json = writeJsonWallet(new File(walletsDir, "mywallet.json"), watchOnly("../elsewhere", existingXpub));

        Storage storage = new Storage(json);
        try {
            Wallet wallet = storage.loadUnencryptedWallet().getWallet();
            Assertions.assertEquals("mywallet", wallet.getName());
        } finally {
            storage.closeAndWait();
        }

        File migrated = new File(walletsDir, "mywallet.mv.db");
        Assertions.assertTrue(migrated.exists(), "mywallet.mv.db missing after migrate");
        Assertions.assertFalse(json.exists(), "opened JSON file should be removed after migrate");
        Assertions.assertFalse(new File(tempDir.toFile(), "elsewhere.mv.db").exists(), "wallet written using the name stored inside the JSON");
        Assertions.assertEquals(existingXpub, readDbXpub(migrated));
    }

    @Test
    public void migrateDoesNotReplaceWalletWithSameInternalName() throws Exception {
        File walletsDir = Files.createDirectory(tempDir.resolve("wallets")).toFile();
        File existing = saveDbWallet(walletsDir, watchOnly("savings", existingXpub));
        Assertions.assertEquals(existingXpub, readDbXpub(existing));

        File opened = writeJsonWallet(new File(walletsDir, "statement.json"), watchOnly("savings", openedXpub));
        openWallet(opened);

        Assertions.assertTrue(existing.exists(), "savings.mv.db missing after migrate");
        Assertions.assertEquals(existingXpub, readDbXpub(existing), "savings.mv.db was replaced by the opened JSON wallet");
    }

    @Test
    public void migrateDoesNotWriteOutsideFolder() throws Exception {
        File walletsDir = Files.createDirectory(tempDir.resolve("wallets")).toFile();
        File downloads = Files.createDirectory(tempDir.resolve("downloads")).toFile();
        File existing = saveDbWallet(walletsDir, watchOnly("coldstorage", existingXpub));
        Assertions.assertEquals(existingXpub, readDbXpub(existing));

        File opened = writeJsonWallet(new File(downloads, "invoice.json"), watchOnly("../wallets/coldstorage", openedXpub));
        openWallet(opened);

        Assertions.assertTrue(existing.exists(), "wallet outside the opened file's folder missing after migrate");
        Assertions.assertEquals(existingXpub, readDbXpub(existing), "wallet outside the opened file's folder was overwritten");
    }

    @Test
    public void migrateRefusesExistingTarget() throws Exception {
        File walletsDir = Files.createDirectory(tempDir.resolve("wallets")).toFile();
        File existing = saveDbWallet(walletsDir, watchOnly("foo", existingXpub));
        File json = writeJsonWallet(new File(walletsDir, "foo.json"), watchOnly("foo", openedXpub));
        byte[] jsonContents = Files.readAllBytes(json.toPath());

        Storage storage = new Storage(json);
        try {
            Assertions.assertThrows(StorageException.class, storage::loadUnencryptedWallet);
        } finally {
            storage.closeAndWait();
        }

        Assertions.assertEquals(existingXpub, readDbXpub(existing), "foo.mv.db was changed by the refused migration");
        Assertions.assertArrayEquals(jsonContents, Files.readAllBytes(json.toPath()), "foo.json was changed by the refused migration");
    }

    @Test
    public void failedMigrateRemovesPartialFile() throws Exception {
        File walletsDir = Files.createDirectory(tempDir.resolve("wallets")).toFile();
        Wallet wallet = watchOnly("broken", openedXpub);
        //A master fingerprint longer than the keystore column allows is only rejected by the insert, after the DB file is created
        wallet.getKeystores().getFirst().setKeyDerivation(new KeyDerivation("60bcd3a7ff", DERIVATION));
        File json = writeJsonWallet(new File(walletsDir, "broken.json"), wallet);
        byte[] jsonContents = Files.readAllBytes(json.toPath());

        Storage storage = new Storage(json);
        try {
            Assertions.assertThrows(UnableToExecuteStatementException.class, storage::loadUnencryptedWallet);
        } finally {
            storage.closeAndWait();
        }

        Assertions.assertFalse(new File(walletsDir, "broken.mv.db").exists(), "partial broken.mv.db left after failed migrate");
        Assertions.assertArrayEquals(jsonContents, Files.readAllBytes(json.toPath()), "broken.json was changed by the failed migration");
    }

    private File createBackupDir(String... backupNames) throws IOException {
        Path backupDir = Files.createTempDirectory("sprw-backup");
        backupDir.toFile().deleteOnExit();
        for(String backupName : backupNames) {
            File backup = backupDir.resolve(backupName).toFile();
            backup.createNewFile();
            backup.deleteOnExit();
        }

        return backupDir.toFile();
    }

    private void assertBackups(File backupDir, PersistenceType persistenceType, String walletFileName, String... expectedBackupNames) {
        Storage storage = new Storage(persistenceType, new File(backupDir.getParentFile(), walletFileName));
        File[] backups = storage.getBackups(backupDir, null);
        Assertions.assertArrayEquals(expectedBackupNames, Arrays.stream(backups).map(File::getName).toArray(String[]::new));
    }

    private static String seedXpub(String mnemonic) throws Exception {
        DeterministicSeed seed = new DeterministicSeed(mnemonic, "", 0, DeterministicSeed.Type.BIP39);
        Keystore keystore = Keystore.fromSeed(seed, PolicyType.SINGLE_HD, KeyDerivation.parsePath(DERIVATION));
        return keystore.getExtendedPublicKey().toString();
    }

    private Wallet watchOnly(String name, String xpub) {
        Wallet wallet = new Wallet(name);
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);
        Keystore keystore = new Keystore("Keystore 1");
        keystore.setKeyDerivation(new KeyDerivation("60bcd3a7", DERIVATION));
        keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(xpub));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), null));
        return wallet;
    }

    private File saveDbWallet(File dir, Wallet wallet) throws Exception {
        File file = new File(dir, wallet.getName() + "." + PersistenceType.DB.getExtension());
        Storage storage = new Storage(PersistenceType.DB, file);
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        try {
            storage.saveWallet(wallet);
        } finally {
            storage.closeAndWait();
        }
        return storage.getWalletFile();
    }

    private File writeJsonWallet(File file, Wallet wallet) throws Exception {
        Storage storage = new Storage(PersistenceType.JSON, file);
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        try {
            storage.saveWallet(wallet);
        } finally {
            storage.closeAndWait();
        }
        return storage.getWalletFile();
    }

    private String readDbXpub(File dbFile) throws Exception {
        Storage storage = new Storage(PersistenceType.DB, dbFile);
        try {
            Wallet w = storage.loadUnencryptedWallet().getWallet();
            return w.getKeystores().getFirst().getExtendedPublicKey().toString();
        } finally {
            storage.closeAndWait();
        }
    }

    private void openWallet(File file) {
        Storage storage = new Storage(file);
        Assertions.assertEquals(PersistenceType.JSON, storage.getType());
        try {
            storage.loadUnencryptedWallet();
        } catch(Exception e) {
            //Refusing to open the file is acceptable
        } finally {
            storage.closeAndWait();
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_NETWORKS_PROPERTY, "false");
        if(tempDir != null) {
            try(Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }
}
