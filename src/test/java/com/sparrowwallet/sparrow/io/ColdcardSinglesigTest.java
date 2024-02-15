package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ColdcardSinglesigTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        Network.set(Network.TESTNET);
        ColdcardSinglesig ccSingleSig = new ColdcardSinglesig();
        Keystore keystore = ccSingleSig.getKeystore(ScriptType.P2SH_P2WPKH, getInputStream("cc-singlesig-keystore-1.json"), null);

        Assertions.assertEquals("Coldcard", keystore.getLabel());
        Assertions.assertEquals("m/49'/1'/123'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("0f056943", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("tpubDCDqt7XXvhAdy1MpSze5nMJA9x8DrdRaKALRRPasfxyHpiqWWEAr9cbDBQ9BcX7cB3up98Pk97U2QQ3xrvQsi5dNPmRYYhdcsKY9wwEY87T"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testImportWitness() throws ImportException {
        Network.set(Network.TESTNET);
        ColdcardSinglesig ccSingleSig = new ColdcardSinglesig();
        Keystore keystore = ccSingleSig.getKeystore(ScriptType.P2WPKH, getInputStream("cc-singlesig-keystore-1.json"), null);

        Assertions.assertEquals("Coldcard", keystore.getLabel());
        Assertions.assertEquals("m/84'/1'/123'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("0f056943", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("tpubDC7jGaaSE66VDB6VhEDFYQSCAyugXmfnMnrMVyHNzW9wryyTxvha7TmfAHd7GRXrr2TaAn2HXn9T8ep4gyNX1bzGiieqcTUNcu2poyntrET"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @AfterEach
    public void tearDown() throws Exception {
        Network.set(null);
    }
}
