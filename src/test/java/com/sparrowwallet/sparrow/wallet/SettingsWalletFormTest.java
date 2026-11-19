package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.Argon2KeyDeriver;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.event.KeystoreEncryptionChangedEvent;
import com.sparrowwallet.sparrow.io.PersistenceType;
import com.sparrowwallet.sparrow.io.Storage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SettingsWalletFormTest {
    @TempDir
    private static Path tempHome;

    private final List<KeystoreEncryptionChangedEvent> encryptionEvents = new ArrayList<>();

    @BeforeAll
    public static void setUpAll() {
        System.setProperty(SparrowWallet.APP_HOME_PROPERTY, tempHome.toString());
    }

    @AfterAll
    public static void tearDownAll() {
        System.clearProperty(SparrowWallet.APP_HOME_PROPERTY);
    }

    @BeforeEach
    public void setUp() {
        EventManager.get().register(this);
    }

    @AfterEach
    public void tearDown() {
        EventManager.get().unregister(this);
    }

    @Subscribe
    public void keystoreEncryptionChanged(KeystoreEncryptionChangedEvent event) {
        encryptionEvents.add(event);
    }

    private static Wallet seedWallet() throws Exception {
        Wallet wallet = new Wallet("settings");
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);
        DeterministicSeed seed = new DeterministicSeed(Utils.hexToBytes(String.format("%032x", 3)), "", 0);
        wallet.getKeystores().add(Keystore.fromSeed(seed, PolicyType.SINGLE_HD, ScriptType.P2WPKH.getDefaultDerivation()));
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), null));
        return wallet;
    }

    private Storage savedStorage(Wallet masterWallet) throws Exception {
        Storage storage = new Storage(PersistenceType.DB, new File(tempHome.toFile(), "settings-" + System.nanoTime() + ".mv.db"));
        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        storage.saveWallet(masterWallet);
        for(Wallet childWallet : masterWallet.getChildWallets()) {
            storage.saveWallet(childWallet);
        }
        return storage;
    }

    @Test
    public void accountCopyIsChildOfMasterCopy() throws Exception {
        Wallet masterWallet = seedWallet();
        Wallet account = masterWallet.addChildWallet(StandardAccount.ACCOUNT_1);
        Storage storage = savedStorage(masterWallet);
        SettingsWalletForm form = new SettingsWalletForm(storage, account, null);

        Wallet accountCopy = form.getWallet();
        Assertions.assertNotSame(account, accountCopy);
        Assertions.assertTrue(accountCopy.getMasterWallet().getChildWallets().stream().anyMatch(child -> child == accountCopy));

        Wallet replacement = accountCopy.copy();
        replacement.setMasterWallet(accountCopy.getMasterWallet());
        form.setWallet(replacement);
        Assertions.assertTrue(replacement.getMasterWallet().getChildWallets().stream().anyMatch(child -> child == replacement));
        Assertions.assertTrue(replacement.getMasterWallet().getChildWallets().stream().noneMatch(child -> child == accountCopy));
        storage.closeAndWait();
    }

    @Test
    public void passwordAddedWithGapLimitChangeEncryptsAccounts() throws Exception {
        Wallet masterWallet = seedWallet();
        Wallet account = masterWallet.addChildWallet(StandardAccount.ACCOUNT_1);
        Storage storage = savedStorage(masterWallet);
        SettingsWalletForm form = new SettingsWalletForm(storage, masterWallet, null);

        Wallet masterCopy = form.getWallet();
        Key key = new Argon2KeyDeriver().deriveKey("pass");
        masterCopy.encrypt(key);
        masterCopy.getChildWallets().forEach(child -> child.encrypt(key));
        form.gapLimitChanged(masterCopy.getGapLimit() + 5);

        form.saveAndRefresh();

        Assertions.assertTrue(encryptionEvents.stream().anyMatch(event -> event.getWallet() == account), "the account's keystores were not re-encrypted");
        Assertions.assertTrue(account.getKeystores().getFirst().getSeed().isEncrypted());
        storage.closeAndWait();
    }
}
