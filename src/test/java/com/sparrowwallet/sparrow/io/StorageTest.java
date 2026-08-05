package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class StorageTest extends IoTest {
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

        Assertions.assertEquals("testd2", wallet.getName());
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

    @AfterEach
    void tearDown() {
        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_NETWORKS_PROPERTY, "false");
    }
}
