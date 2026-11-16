package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.KeyDerivation;
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
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.Locale;

public class UROutputDescriptorTest extends IoTest {

    @Test
    public void testNativeSegwitMultisigURExport() throws Exception {
        assertMultisigURExport(
                "cc-multisig-export-1.txt",
                Network.MAINNET,
                List.of(
                        ScriptExpression.WITNESS_SCRIPT_HASH,
                        ScriptExpression.SORTED_MULTISIG
                ),
                2, 4
        );
    }

    @Test
    public void testNestedSegwitMultisigURExport() throws Exception {
        assertMultisigURExport(
                "cc-multisig-export-2.txt",
                Network.TESTNET,
                List.of(
                        ScriptExpression.SCRIPT_HASH,
                        ScriptExpression.WITNESS_SCRIPT_HASH,
                        ScriptExpression.SORTED_MULTISIG
                ),
                2, 4
        );
    }

    @Test
    public void testMultiDerivationMultisigURExport() throws Exception {
        assertMultisigURExport(
                "cc-multisig-export-multideriv.txt",
                Network.MAINNET,
                List.of(
                        ScriptExpression.WITNESS_SCRIPT_HASH,
                        ScriptExpression.SORTED_MULTISIG
                ),
                3, 3
        );
    }

    @Test
    public void testMultisigURExportCosignerOrdering() throws Exception {
        Network previousNetwork = Network.get();

        try {
            Network.set(Network.MAINNET);

            Wallet wallet = new ColdcardMultisig().importWallet(
                    getInputStream("cc-multisig-export-1.txt"),
                    null
            );

            CryptoOutput original = (CryptoOutput)
                    SettingsController.getUROutputDescriptor(wallet);

            List<String> originalOrder = original.getMultiKey()
                    .getHdKeys().stream()
                    .map(key -> Utils.bytesToHex(key.getKey()))
                    .toList();

            Collections.reverse(wallet.getKeystores());

            CryptoOutput reversed = (CryptoOutput)
                    SettingsController.getUROutputDescriptor(wallet);

            List<String> reversedOrder = reversed.getMultiKey()
                    .getHdKeys().stream()
                    .map(key -> Utils.bytesToHex(key.getKey()))
                    .toList();

            Assertions.assertEquals(
                    originalOrder,
                    reversedOrder,
                    "Cosigner export order should not depend on keystore insertion order"
            );

        } finally {
            Network.set(previousNetwork);
        }
    }

    private void assertMultisigURExport(
            String filename,
            Network network,
            List<ScriptExpression> expectedExpressions,
            int expectedThreshold,
            int expectedCosigners) throws Exception {

        Network previousNetwork = Network.get();

        try {
            Network.set(network);

            Wallet wallet;
            try(InputStream inputStream = getInputStream(filename)) {
                Assertions.assertNotNull(inputStream, "Missing fixture: " + filename);
                wallet = new ColdcardMultisig().importWallet(inputStream, null);
            }

            // Export the wallet using Sparrow's UR conversion logic
            CryptoOutput output = Assertions.assertInstanceOf(
                    CryptoOutput.class,
                    SettingsController.getUROutputDescriptor(wallet)
            );

            // Verify script expressions
            Assertions.assertEquals(
                    expectedExpressions,
                    output.getScriptExpressions()
            );

            // Verify multisig policy
            MultiKey multiKey = output.getMultiKey();

            Assertions.assertNotNull(multiKey);
            Assertions.assertEquals(expectedThreshold, multiKey.getThreshold());
            Assertions.assertEquals(expectedCosigners, multiKey.getHdKeys().size());

            // Verify each cosigner's information is preserved
            for(Keystore keystore : wallet.getKeystores()) {
                ExtendedKey extendedKey = keystore.getExtendedPublicKey();
                KeyDerivation derivation = keystore.getKeyDerivation();

                List<CryptoHDKey> matches = multiKey.getHdKeys().stream()
                        .filter(key -> Arrays.equals(
                                extendedKey.getKey().getPubKey(),
                                key.getKey()))
                        .toList();

                Assertions.assertEquals(1, matches.size(),
                        "Expected one matching cosigner: " + keystore.getLabel());

                CryptoHDKey exportedKey = matches.getFirst();

                // Verify public key and chain code
                Assertions.assertArrayEquals(
                        extendedKey.getKey().getPubKey(),
                        exportedKey.getKey()
                );

                Assertions.assertArrayEquals(
                        extendedKey.getKey().getChainCode(),
                        exportedKey.getChainCode()
                );

                // Verify master fingerprint
                Assertions.assertEquals(
                        derivation.getMasterFingerprint().toLowerCase(Locale.ROOT),
                        Utils.bytesToHex(
                                exportedKey.getOrigin().getSourceFingerprint()
                        ).toLowerCase(Locale.ROOT)
                );

                // Verify the individual cosigner derivation path
                Assertions.assertEquals(
                        derivation.getDerivationPath().substring(2),
                        exportedKey.getOrigin().getPath()
                );

                // Verify Bitcoin network metadata
                Assertions.assertEquals(
                        CryptoCoinInfo.Type.BITCOIN,
                        exportedKey.getUseInfo().getType()
                );

                Assertions.assertEquals(
                        network == Network.MAINNET
                                ? CryptoCoinInfo.Network.MAINNET
                                : CryptoCoinInfo.Network.TESTNET,
                        exportedKey.getUseInfo().getNetwork()
                );

                // Confirm no private key material is exported
                Assertions.assertFalse(exportedKey.isPrivateKey());
            }
        } finally {
            Network.set(previousNetwork);
        }
    }

}
