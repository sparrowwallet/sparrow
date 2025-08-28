package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BitkeyMultisigTest extends IoTest {

    @BeforeEach
    public void setUp() {
        Network.set(Network.MAINNET);
    }

    @Test
    public void testImport() throws ImportException {
        BitkeyMultisig bitkeyMultisig = new BitkeyMultisig();
        Wallet wallet = bitkeyMultisig.importWallet(getInputStream("bitkey-multisig-export.txt"), null);

        Assertions.assertNotNull(wallet);
        Assertions.assertEquals("Bitkey Multisig", wallet.getName());
        Assertions.assertEquals(PolicyType.MULTI, wallet.getPolicyType());
        Assertions.assertEquals(ScriptType.P2WSH, wallet.getScriptType());
        Assertions.assertEquals(3, wallet.getKeystores().size());
        Assertions.assertEquals(2, wallet.getDefaultPolicy().getNumSignaturesRequired());

        // Keystore 1
        Assertions.assertEquals("c11d36d2", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals("m/84'/0'/0'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6D4Sz2PtJCj14ZmnCTzmGgMRttx2iU3MXbyCzEGetB2D4DsRz2Rk69Hp2M94Nhkn6QBZ5JTa1EW82CtjHeTe1wqQXMKYY6Pq7qVaz3uXdZ2"), wallet.getKeystores().get(0).getExtendedPublicKey());
        Assertions.assertEquals(WalletModel.BITKEY, wallet.getKeystores().get(0).getWalletModel());

        // Keystore 2
        Assertions.assertEquals("647b89ea", wallet.getKeystores().get(1).getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals("m/84'/0'/0'", wallet.getKeystores().get(1).getKeyDerivation().getDerivationPath());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6CbVC3uhwPsQfAMC6ZVqCZsEx4BtNESnCLe3N8WqxAp46J2kjptausZRr11dCVh2WHamnEPrC8haz4iLR4sveUcqQx2iUawK41AY5RgyK8z"), wallet.getKeystores().get(1).getExtendedPublicKey());
        Assertions.assertEquals(WalletModel.BITKEY, wallet.getKeystores().get(1).getWalletModel());

        // Keystore 3
        Assertions.assertEquals("02bfb691", wallet.getKeystores().get(2).getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals("m/84'/0'/0'", wallet.getKeystores().get(2).getKeyDerivation().getDerivationPath());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6DAGAFL3RnuYbzs9zbbDeGG1uEMj8tTpp8EEy4DMiPXHNaX5jVdeixoZN5jpDGbPa6a62LMqH4FCi9JDs4GF9qx89bRmVoHeznSfx7svqYN"), wallet.getKeystores().get(2).getExtendedPublicKey());
        Assertions.assertEquals(WalletModel.BITKEY, wallet.getKeystores().get(2).getWalletModel());
    }



    @Test
    public void testInvalidFormat() throws ImportException {
        BitkeyMultisig bitkeyMultisig = new BitkeyMultisig();
        Assertions.assertThrows(ImportException.class, () -> 
            bitkeyMultisig.importWallet(getInputStream("bitkey-invalid-format.txt"), null));
    }

    @AfterEach
    public void tearDown() {
        Network.set(null);
    }
} 