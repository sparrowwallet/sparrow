package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.io.Storage.SparrowDirectories;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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

    @Nested
    @Execution(ExecutionMode.SAME_THREAD)
    class SparrowDirectoriesTests {
        private void assertFilesAreSame(Collection<File> files) {
            Assertions.assertEquals(1, Set.copyOf(files).size(), "All files should be the same");
        }

        abstract class SharedTests {
            String originalAppHome;

            @BeforeEach
            void setupShared() {
                originalAppHome = System.getProperty(SparrowWallet.APP_HOME_PROPERTY);
            }

            private void assertPathIsInDefaultLocation(File actual) {
                String msg = Storage.osTypeSupplier.get() == OsType.WINDOWS
                    ? "Path should be in APPHOME"
                    : "Path should be in user.home";

                String path = actual.getAbsolutePath();
                String expectedPathPart = Storage.getUserHomeDir().getAbsolutePath();

                Assertions.assertTrue(path.contains(expectedPathPart), msg);

            }

            @Test
            void respectsAppHomeProperty(@TempDir File tempAppHome) {
                System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempAppHome.getAbsolutePath());

                SparrowDirectories sparrowHomeDirs = SparrowDirectories.getHomeDirs(false);

                Assertions.assertEquals(tempAppHome, sparrowHomeDirs.config());
                assertFilesAreSame(sparrowHomeDirs.asMap().values());
            }

            @Test
            void respectsDefaultOverride(@TempDir File tempAppHome) {
                System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempAppHome.getAbsolutePath());

                SparrowDirectories sparrowHomeDirs = SparrowDirectories.getHomeDirs(true);

                Assertions.assertNotEquals(tempAppHome, sparrowHomeDirs.config());
                assertPathIsInDefaultLocation(sparrowHomeDirs.config());
                assertFilesAreSame(sparrowHomeDirs.asMap().values());
            }

            @Test
            void useDefaultIfNoAppHomeProp() {
                System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);

                SparrowDirectories sparrowHomeDirs = SparrowDirectories.getHomeDirs(false);

                assertPathIsInDefaultLocation(sparrowHomeDirs.config());
                assertFilesAreSame(sparrowHomeDirs.asMap().values());
            }

            @AfterEach
            void tearDownShared() {
               if (originalAppHome != null) {
                   System.setProperty(SparrowWallet.APP_HOME_PROPERTY, originalAppHome);
               } else {
                   System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
               }
            }
        }

        abstract class SharedUnixAndMacOsTests extends SharedTests {
            @Test
            void sparrowDirectoriesAreOne() {
                File configHome = SparrowDirectories.getHomeDirs().config();
                File dataHome = SparrowDirectories.getHomeDirs().data();
                File stateHome = SparrowDirectories.getHomeDirs().state();
                File cacheHome = SparrowDirectories.getHomeDirs().cache();

                assertFilesAreSame(List.of(configHome, dataHome, stateHome, cacheHome));
            }

            @Test
            void sparrowDirectoryLocation() {
                // precondition: user.home must be set
                String userHome = System.getProperty("user.home");
                Assertions.assertNotNull(userHome);

                File configHome = SparrowDirectories.getHomeDirs().config();

                String path = configHome.getAbsolutePath();
                Assertions.assertTrue(path.contains(userHome), "Path should contain user.home");
                Assertions.assertTrue(path.contains(Storage.SPARROW_DIR), "Path should contain SPARROW_DIR");
            }
        }

        @Nested
        class OsTypeWindows extends SharedTests {
            @BeforeEach
            void setup(@TempDir File tempDir) {
                Storage.osTypeSupplier = () -> OsType.WINDOWS;
                Storage.envRetriever = (String envVar) -> Storage.ENV_APPDATA.equals(envVar) ? tempDir.getAbsolutePath() : System.getenv(envVar);
            }

            @Test
            void sparrowDirectoriesAreOne() {
                File configHome = SparrowDirectories.getHomeDirs().config();
                File dataHome = SparrowDirectories.getHomeDirs().data();
                File stateHome = SparrowDirectories.getHomeDirs().state();
                File cacheHome = SparrowDirectories.getHomeDirs().cache();

                Assertions.assertTrue(Stream.of(dataHome, stateHome, cacheHome).allMatch(configHome::equals), "All files should have same path");
            }

            @Test
            void sparrowDirectoryLocation() {
                File configHome = SparrowDirectories.getHomeDirs().config();

                String path = configHome.getAbsolutePath();
                String appDataPath = Storage.envRetriever.apply(Storage.ENV_APPDATA);
                Assertions.assertTrue(path.contains(appDataPath), "Path should contain APPDATA in path");
                Assertions.assertTrue(path.contains(Storage.WINDOWS_SPARROW_DIR), "Path should contain WINDOWS_SPARROW_DIR");
            }

            @AfterEach
            void tearDown() {
                Storage.envRetriever = System::getenv;
            }
        }

        @Nested
        class OsTypeUnix extends SharedUnixAndMacOsTests {
            @BeforeEach
            void setup() {
                Storage.osTypeSupplier = () -> OsType.UNIX;
            }
        }

        @Nested
        class OsTypeMacOs extends SharedUnixAndMacOsTests {
            @BeforeEach
            void setup() {
                Storage.osTypeSupplier = () -> OsType.MACOS;
            }
        }

        @AfterEach
        void tearDown() {
            Storage.osTypeSupplier = OsType::getCurrent;
        }
    }

    @AfterEach
    void tearDown() {
        System.setProperty(Wallet.ALLOW_DERIVATIONS_MATCHING_OTHER_NETWORKS_PROPERTY, "false");
    }
}
