package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
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
    public void testNativeSegwitMultisigURExport() throws Exception {
        List<ScriptExpression> expressions = List.of(ScriptExpression.WITNESS_SCRIPT_HASH, ScriptExpression.SORTED_MULTISIG);
        assertMultisigURExport("cc-multisig-export-1.txt", Network.MAINNET, expressions, 2, 4);
    }

    @Test
    public void testNestedSegwitMultisigURExport() throws Exception {
        List<ScriptExpression> expressions = List.of(ScriptExpression.SCRIPT_HASH, ScriptExpression.WITNESS_SCRIPT_HASH, ScriptExpression.SORTED_MULTISIG);
        assertMultisigURExport("cc-multisig-export-2.txt", Network.TESTNET, expressions, 2, 4);
    }

    @Test
    public void testMultiDerivationMultisigURExport() throws Exception {
        List<ScriptExpression> expressions = List.of(ScriptExpression.WITNESS_SCRIPT_HASH, ScriptExpression.SORTED_MULTISIG);
        assertMultisigURExport("cc-multisig-export-multideriv.txt", Network.MAINNET, expressions, 3, 3);
    }

    private void assertMultisigURExport(String filename, Network network, List<ScriptExpression> expectedExpressions,
                                        int expectedThreshold, int expectedCosigners) throws Exception {
        Network.set(network);

        Wallet wallet;
        try(InputStream inputStream = getInputStream(filename)) {
            Assertions.assertNotNull(inputStream, "Missing fixture: " + filename);
            wallet = new ColdcardMultisig().importWallet(inputStream, null);
        }

        CryptoOutput output = Assertions.assertInstanceOf(CryptoOutput.class, SettingsController.getUROutputDescriptor(wallet));

        Assertions.assertEquals(expectedExpressions, output.getScriptExpressions());

        MultiKey multiKey = output.getMultiKey();

        Assertions.assertNotNull(multiKey);
        Assertions.assertEquals(expectedThreshold, multiKey.getThreshold());
        Assertions.assertEquals(expectedCosigners, multiKey.getHdKeys().size());

        for(Keystore keystore : wallet.getKeystores()) {
            ExtendedKey extendedKey = keystore.getExtendedPublicKey();
            KeyDerivation derivation = keystore.getKeyDerivation();

            List<CryptoHDKey> matches = multiKey.getHdKeys().stream().filter(key -> Arrays.equals(extendedKey.getKey().getPubKey(), key.getKey())).toList();

            Assertions.assertEquals(1, matches.size(), "Expected one matching cosigner: " + keystore.getLabel());

            CryptoHDKey exportedKey = matches.getFirst();

            Assertions.assertArrayEquals(extendedKey.getKey().getChainCode(), exportedKey.getChainCode());
            Assertions.assertArrayEquals(extendedKey.getParentFingerprint(), exportedKey.getParentFingerprint());

            String masterFingerprint = Utils.bytesToHex(exportedKey.getOrigin().getSourceFingerprint());
            Assertions.assertEquals(derivation.getMasterFingerprint().toLowerCase(Locale.ROOT), masterFingerprint.toLowerCase(Locale.ROOT));
            Assertions.assertEquals(derivation.getDerivationPath().substring(2), exportedKey.getOrigin().getPath());
            Assertions.assertEquals(CryptoCoinInfo.Type.BITCOIN, exportedKey.getUseInfo().getType());
            Assertions.assertEquals(network == Network.MAINNET ? CryptoCoinInfo.Network.MAINNET : CryptoCoinInfo.Network.TESTNET, exportedKey.getUseInfo().getNetwork());
            Assertions.assertFalse(exportedKey.isPrivateKey());
        }
    }
}
