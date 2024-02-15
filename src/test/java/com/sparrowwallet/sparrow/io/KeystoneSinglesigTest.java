package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KeystoneSinglesigTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        KeystoneSinglesig keystoneSingleSig = new KeystoneSinglesig();
        Keystore keystore = keystoneSingleSig.getKeystore(ScriptType.P2WPKH, getInputStream("keystone-singlesig-keystore-1.txt"), null);

        Assertions.assertEquals("Keystone", keystore.getLabel());
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("5271c071", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("zpub6rcabYFcdr41zyUNRWRyHYs2Sm86E5XV8RjjRzTFYsiCngteeZnkwaF2xuhjmM6kpHjuNpFW42BMhzPmFwXt48e1FhddMB7xidZzN4SF24K"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testIncorrectScriptType() throws ImportException {
        KeystoneSinglesig keystoneSingleSig = new KeystoneSinglesig();
        Assertions.assertThrows(ImportException.class, () -> keystoneSingleSig.getKeystore(ScriptType.P2SH_P2WPKH, getInputStream("keystone-singlesig-keystore-1.txt"), null));
    }
}
