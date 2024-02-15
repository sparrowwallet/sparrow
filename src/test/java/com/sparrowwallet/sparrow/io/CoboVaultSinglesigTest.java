package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CoboVaultSinglesigTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        CoboVaultSinglesig coboSingleSig = new CoboVaultSinglesig();
        Keystore keystore = coboSingleSig.getKeystore(ScriptType.P2WPKH, getInputStream("cobo-singlesig-keystore-1.json"), null);

        Assertions.assertEquals("Cobo Vault", keystore.getLabel());
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("73c5da0a", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testIncorrectScriptType() throws ImportException {
        CoboVaultSinglesig coboSingleSig = new CoboVaultSinglesig();
        Assertions.assertThrows(ImportException.class, () -> coboSingleSig.getKeystore(ScriptType.P2SH_P2WPKH, getInputStream("cobo-singlesig-keystore-1.json"), null));
    }
}
