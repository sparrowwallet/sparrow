package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.registry.CryptoCoinInfo;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;
import com.sparrowwallet.hummingbird.registry.CryptoOutput;
import com.sparrowwallet.hummingbird.registry.MultiKey;
import com.sparrowwallet.hummingbird.registry.ScriptExpression;
import com.sparrowwallet.sparrow.wallet.SettingsController;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class UROutputDescriptorTest extends IoTest {

    @Test
    public void testNativeSegwitSinglesigURExport() throws Exception {
        Network.set(Network.TESTNET);
        Wallet wallet = importTestWallet("cc-singlesig-keystore-1.json", new ColdcardSinglesig());
        List<ScriptExpression> expressions = List.of(ScriptExpression.WITNESS_PUBLIC_KEY_HASH);
        assertSinglesigURExport(wallet, expressions);
    }

    @Test
    public void testNestedSegwitSinglesigURExport() throws Exception {
        Network.set(Network.MAINNET);
        Wallet wallet = importTestWallet("specter-wallet.json", new SpecterDesktop());
        List<ScriptExpression> expressions = List.of(ScriptExpression.SCRIPT_HASH, ScriptExpression.WITNESS_PUBLIC_KEY_HASH);
        assertSinglesigURExport(wallet, expressions);
    }

    @Test
    public void testTaprootSinglesigURExport() throws Exception {
        Network.set(Network.MAINNET);
        Wallet wallet = importTestWallet("descriptor-taproot.txt", new Descriptor());
        List<ScriptExpression> expressions = List.of(ScriptExpression.TAPROOT);
        assertSinglesigURExport(wallet, expressions);
    }

    @Test
    public void testLegacySinglesigURExport() throws Exception {
        Network.set(Network.TESTNET);
        Wallet wallet = importTestWallet("descriptor-legacy.txt", new Descriptor());
        List<ScriptExpression> expressions = List.of(ScriptExpression.PUBLIC_KEY_HASH);
        assertSinglesigURExport(wallet, expressions);
    }

    @Test
    public void testNativeSegwitMultisigURExport() throws Exception {
        Network.set(Network.MAINNET);
        Wallet wallet = importTestWallet("cc-multisig-export-1.txt", new ColdcardMultisig());
        List<ScriptExpression> expressions = List.of(ScriptExpression.WITNESS_SCRIPT_HASH, ScriptExpression.SORTED_MULTISIG);
        assertMultisigURExport(wallet, expressions, 2, 4);
    }

    @Test
    public void testNestedSegwitMultisigURExport() throws Exception {
        Network.set(Network.TESTNET);
        Wallet wallet = importTestWallet("cc-multisig-export-2.txt", new ColdcardMultisig());
        List<ScriptExpression> expressions = List.of(ScriptExpression.SCRIPT_HASH, ScriptExpression.WITNESS_SCRIPT_HASH, ScriptExpression.SORTED_MULTISIG);
        assertMultisigURExport(wallet, expressions, 2, 4);
    }

    @Test
    public void testMultiDerivationMultisigURExport() throws Exception {
        Network.set(Network.MAINNET);
        Wallet wallet = importTestWallet("cc-multisig-export-multideriv.txt", new ColdcardMultisig());
        List<ScriptExpression> expressions = List.of(ScriptExpression.WITNESS_SCRIPT_HASH, ScriptExpression.SORTED_MULTISIG);
        assertMultisigURExport(wallet, expressions, 3, 3);
    }

    private Wallet importTestWallet(String filename, WalletImport importer) throws Exception {
        try(InputStream inputStream = getInputStream(filename)) {
            Assertions.assertNotNull(inputStream, "Missing fixture: " + filename);
            return importer.importWallet(inputStream, null);
        }
    }

    private void assertCryptoHDKey(Keystore keystore, CryptoHDKey exportedKey) {
        ExtendedKey extendedKey = keystore.getExtendedPublicKey();
        KeyDerivation derivation = keystore.getKeyDerivation();

        Assertions.assertArrayEquals(extendedKey.getKey().getPubKey(), exportedKey.getKey());
        Assertions.assertArrayEquals(extendedKey.getKey().getChainCode(), exportedKey.getChainCode());
        Assertions.assertArrayEquals(extendedKey.getParentFingerprint(), exportedKey.getParentFingerprint());

        String masterFingerprint = Utils.bytesToHex(exportedKey.getOrigin().getSourceFingerprint());
        Assertions.assertEquals(derivation.getMasterFingerprint().toLowerCase(Locale.ROOT), masterFingerprint.toLowerCase(Locale.ROOT));
        Assertions.assertEquals(derivation.getDerivationPath().substring(2), exportedKey.getOrigin().getPath());
        Assertions.assertEquals(CryptoCoinInfo.Type.BITCOIN, exportedKey.getUseInfo().getType());
        Assertions.assertEquals(Network.get() == Network.MAINNET ? CryptoCoinInfo.Network.MAINNET : CryptoCoinInfo.Network.TESTNET, exportedKey.getUseInfo().getNetwork());
        Assertions.assertFalse(exportedKey.isPrivateKey());
    }


    private void assertSinglesigURExport(Wallet wallet, List<ScriptExpression> expectedExpressions) {
        Assertions.assertEquals(PolicyType.SINGLE_HD, wallet.getPolicyType());
        Assertions.assertEquals(1, wallet.getKeystores().size());

        CryptoOutput output = Assertions.assertInstanceOf(CryptoOutput.class, SettingsController.getUROutputDescriptor(wallet));

        Assertions.assertEquals(expectedExpressions, output.getScriptExpressions());

        CryptoHDKey exportedKey = output.getHdKey();
        Assertions.assertNotNull(exportedKey);

        assertCryptoHDKey(wallet.getKeystores().getFirst(), exportedKey);
    }


    private void assertMultisigURExport(Wallet wallet, List<ScriptExpression> expectedExpressions, int expectedThreshold, int expectedCosigners) {

        CryptoOutput output = Assertions.assertInstanceOf(CryptoOutput.class, SettingsController.getUROutputDescriptor(wallet));

        Assertions.assertEquals(expectedExpressions, output.getScriptExpressions());

        MultiKey multiKey = output.getMultiKey();

        Assertions.assertNotNull(multiKey);
        Assertions.assertEquals(expectedThreshold, multiKey.getThreshold());
        Assertions.assertEquals(expectedCosigners, multiKey.getHdKeys().size());

        for(Keystore keystore : wallet.getKeystores()) {
            ExtendedKey extendedKey = keystore.getExtendedPublicKey();

            List<CryptoHDKey> matches = multiKey.getHdKeys().stream().filter(key -> Arrays.equals(extendedKey.getKey().getPubKey(), key.getKey())).toList();

            Assertions.assertEquals(1, matches.size(), "Expected one matching cosigner: " + keystore.getLabel());
            assertCryptoHDKey(keystore, matches.getFirst());
        }
    }
}
