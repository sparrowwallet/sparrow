package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentScanAddress;
import com.sparrowwallet.drongo.wallet.Keystore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KeystoneSinglesigTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        KeystoneSinglesig keystoneSingleSig = new KeystoneSinglesig();
        Keystore keystore = keystoneSingleSig.getKeystore(PolicyType.SINGLE_HD, ScriptType.P2WPKH, getInputStream("keystone-singlesig-keystore-1.txt"), null);

        Assertions.assertEquals("Keystone", keystore.getLabel());
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("5271c071", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("zpub6rcabYFcdr41zyUNRWRyHYs2Sm86E5XV8RjjRzTFYsiCngteeZnkwaF2xuhjmM6kpHjuNpFW42BMhzPmFwXt48e1FhddMB7xidZzN4SF24K"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testIncorrectScriptType() throws ImportException {
        KeystoneSinglesig keystoneSingleSig = new KeystoneSinglesig();
        Assertions.assertThrows(ImportException.class, () -> keystoneSingleSig.getKeystore(PolicyType.SINGLE_HD, ScriptType.P2SH_P2WPKH, getInputStream("keystone-singlesig-keystore-1.txt"), null));
    }

    @Test
    public void testImportSilentPayments() throws ImportException {
        Network.set(Network.TESTNET);
        KeystoneSinglesig keystoneSingleSig = new KeystoneSinglesig();
        Keystore keystore = keystoneSingleSig.getKeystore(PolicyType.SINGLE_SP, ScriptType.P2TR, getInputStream("keystone-singlesig-sp-keystore-1.txt"), null);

        Assertions.assertEquals("Keystone", keystore.getLabel());
        Assertions.assertEquals("m/352'/1'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("0f056943", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertNull(keystore.getExtendedPublicKey());
        Assertions.assertNotNull(keystore.getSilentPaymentScanAddress());
        Assertions.assertEquals(SilentPaymentScanAddress.fromKeyString("tspscan1q05wxw5wc7wqmkf8cnfc6ry76qej8vhr3a3mmxmwgv35s0tlw24fs82k0npv2hv6p97s8sd9t7vpf44kluka9w863zjwxzfrym2ay9ccfzt06c4"),
                keystore.getSilentPaymentScanAddress());
        Assertions.assertTrue(keystore.isValid());
    }

    @AfterEach
    public void tearDown() throws Exception {
        Network.set(null);
    }
}
