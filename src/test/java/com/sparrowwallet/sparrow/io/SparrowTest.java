package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.Argon2KeyDeriver;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class SparrowTest {
    private static final String TEST_MNEMONIC = "response seminar brave tip suit recall often sound stick owner lottery motion";
    private static final String TEST_XPUB = "xpub6BrhGFTWPd3DXo8s2BPxHHzCmBCyj8QvamcEUaq8EDwnwXpvvcU9LzpJqENHcqHkqwTn2vPhynGVoEqj3PAB3NxnYZrvCsSfoCniJKaggdy";
    private static final String DERIVATION = "m/84'/0'/0'";

    private Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("sprw-sparrow-import");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(tempDir != null) {
            try(Stream<Path> paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private Wallet createSeedWallet() throws Exception {
        Wallet wallet = new Wallet("Seed Wallet");
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);

        DeterministicSeed seed = new DeterministicSeed(TEST_MNEMONIC, "", 0, DeterministicSeed.Type.BIP39);
        Keystore keystore = Keystore.fromSeed(seed, PolicyType.SINGLE_HD, KeyDerivation.parsePath(DERIVATION));
        keystore.setLabel("Keystore 1");
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), null));

        return wallet;
    }

    private Wallet createWatchOnlyWallet() {
        Wallet wallet = new Wallet("Watch Only Wallet");
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);

        Keystore keystore = new Keystore("Keystore 1");
        keystore.setSource(KeystoreSource.SW_WATCH);
        keystore.setWalletModel(WalletModel.SPARROW);
        keystore.setKeyDerivation(new KeyDerivation("60bcd3a7", DERIVATION));
        keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(TEST_XPUB));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), null));

        return wallet;
    }

    private File saveWallet(Wallet wallet, CharSequence password) throws Exception {
        Storage storage = new Storage(PersistenceType.DB, tempDir.resolve(wallet.getName() + "." + PersistenceType.DB.getExtension()).toFile());
        storage.setKeyDeriver(new Argon2KeyDeriver());

        if(password == null) {
            storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
        } else {
            ECKey encryptionFullKey = storage.getKeyDeriver().deriveECKey(password);
            Key key = new Key(encryptionFullKey.getPrivKeyBytes(), storage.getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
            wallet.encrypt(key);
            storage.setEncryptionPubKey(ECKey.fromPublicOnly(encryptionFullKey));
        }

        try {
            storage.saveWallet(wallet);
        } finally {
            storage.closeAndWait();
        }

        return storage.getWalletFile();
    }

    private Wallet importWallet(File walletFile, String password) throws Exception {
        try(ByteArrayInputStream inputStream = new ByteArrayInputStream(Files.readAllBytes(walletFile.toPath()))) {
            return new Sparrow().importWallet(inputStream, password);
        }
    }

    @Test
    public void encryptedSeedWalletImports() throws Exception {
        Wallet wallet = createSeedWallet();
        ExtendedKey expectedXpub = wallet.getKeystores().getFirst().getExtendedPublicKey();
        File walletFile = saveWallet(wallet, "pass");

        Wallet imported = importWallet(walletFile, "pass");
        Assertions.assertEquals(expectedXpub, imported.getKeystores().getFirst().getExtendedPublicKey());
        Assertions.assertDoesNotThrow(imported::checkWallet);
    }

    @Test
    public void unencryptedSeedWalletImports() throws Exception {
        Wallet wallet = createSeedWallet();
        ExtendedKey expectedXpub = wallet.getKeystores().getFirst().getExtendedPublicKey();
        File walletFile = saveWallet(wallet, null);

        Wallet imported = importWallet(walletFile, null);
        Assertions.assertEquals(expectedXpub, imported.getKeystores().getFirst().getExtendedPublicKey());
        Assertions.assertDoesNotThrow(imported::checkWallet);
    }

    @Test
    public void watchOnlyWalletImports() throws Exception {
        File walletFile = saveWallet(createWatchOnlyWallet(), null);

        Wallet imported = importWallet(walletFile, null);
        Assertions.assertEquals(ExtendedKey.fromDescriptor(TEST_XPUB), imported.getKeystores().getFirst().getExtendedPublicKey());
        Assertions.assertDoesNotThrow(imported::checkWallet);
    }
}
