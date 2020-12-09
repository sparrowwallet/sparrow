package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import org.junit.Assert;
import org.junit.Test;

public class SpecterDIYTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        Network.set(Network.TESTNET);
        SpecterDIY specterDIY = new SpecterDIY();
        Keystore keystore = specterDIY.getKeystore(ScriptType.P2WPKH, getInputStream("specter-diy-keystore.txt"), null);

        Assert.assertEquals("Specter DIY", keystore.getLabel());
        Assert.assertEquals("m/84'/1'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assert.assertEquals("b317ec86", keystore.getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals(ExtendedKey.fromDescriptor("vpub5YHLPnkkpPW1ecL7Di7Gv2wDHDtBNqRdt17gMULpxJ27ZA1MmW7xbZjdg1S7d5JKaJ8CiZEmRUHrEB6CGuLomA6ioVa1Pcke6fEb5CzDBU1"), keystore.getExtendedPublicKey());
        Assert.assertTrue(keystore.isValid());
        Network.set(Network.MAINNET);
    }
}
