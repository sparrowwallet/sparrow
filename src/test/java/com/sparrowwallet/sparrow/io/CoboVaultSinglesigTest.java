package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import org.junit.Assert;
import org.junit.Test;

public class CoboVaultSinglesigTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        CoboVaultSinglesig coboSingleSig = new CoboVaultSinglesig();
        Keystore keystore = coboSingleSig.getKeystore(ScriptType.P2WPKH, getInputStream("cobo-singlesig-keystore-1.json"), null);

        Assert.assertEquals("Cobo Vault", keystore.getLabel());
        Assert.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assert.assertEquals("73c5da0a", keystore.getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals(ExtendedKey.fromDescriptor("zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"), keystore.getExtendedPublicKey());
        Assert.assertTrue(keystore.isValid());
    }

    @Test(expected = ImportException.class)
    public void testIncorrectScriptType() throws ImportException {
        CoboVaultSinglesig coboSingleSig = new CoboVaultSinglesig();
        Keystore keystore = coboSingleSig.getKeystore(ScriptType.P2SH_P2WPKH, getInputStream("cobo-singlesig-keystore-1.json"), null);

        Assert.assertEquals("Cobo Vault", keystore.getLabel());
        Assert.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assert.assertEquals("73c5da0a", keystore.getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals(ExtendedKey.fromDescriptor("zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"), keystore.getExtendedPublicKey());
        Assert.assertTrue(keystore.isValid());
    }
}
