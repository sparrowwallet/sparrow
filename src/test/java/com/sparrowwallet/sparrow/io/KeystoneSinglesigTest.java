package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import org.junit.Assert;
import org.junit.Test;

public class KeystoneSinglesigTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        KeystoneSinglesig keystoneSingleSig = new KeystoneSinglesig();
        Keystore keystore = keystoneSingleSig.getKeystore(ScriptType.P2WPKH, getInputStream("keystone-singlesig-keystore-1.txt"), null);

        Assert.assertEquals("Keystone", keystore.getLabel());
        Assert.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assert.assertEquals("5271c071", keystore.getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals(ExtendedKey.fromDescriptor("zpub6rcabYFcdr41zyUNRWRyHYs2Sm86E5XV8RjjRzTFYsiCngteeZnkwaF2xuhjmM6kpHjuNpFW42BMhzPmFwXt48e1FhddMB7xidZzN4SF24K"), keystore.getExtendedPublicKey());
        Assert.assertTrue(keystore.isValid());
    }

    @Test(expected = ImportException.class)
    public void testIncorrectScriptType() throws ImportException {
        KeystoneSinglesig keystoneSingleSig = new KeystoneSinglesig();
        Keystore keystore = keystoneSingleSig.getKeystore(ScriptType.P2SH_P2WPKH, getInputStream("keystone-singlesig-keystore-1.txt"), null);

        Assert.assertEquals("Keystone", keystore.getLabel());
        Assert.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assert.assertEquals("5271c071", keystore.getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals(ExtendedKey.fromDescriptor("zpub6rcabYFcdr41zyUNRWRyHYs2Sm86E5XV8RjjRzTFYsiCngteeZnkwaF2xuhjmM6kpHjuNpFW42BMhzPmFwXt48e1FhddMB7xidZzN4SF24K"), keystore.getExtendedPublicKey());
        Assert.assertTrue(keystore.isValid());
    }
}
