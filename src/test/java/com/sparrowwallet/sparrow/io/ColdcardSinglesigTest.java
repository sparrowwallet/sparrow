package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.Assert;
import org.junit.Test;

public class ColdcardSinglesigTest extends ImportExportTest {
    @Test
    public void testImport() throws ImportException {
        ColdcardSinglesig ccSingleSig = new ColdcardSinglesig();
        Wallet wallet = ccSingleSig.importWallet(ScriptType.P2PKH, getInputStream("cc-wallet-dump.txt"));
        Assert.assertEquals(PolicyType.SINGLE, wallet.getPolicyType());

        Assert.assertEquals("Coldcard 3D88D0CF", wallet.getName());
        Assert.assertEquals(ScriptType.P2PKH, wallet.getScriptType());
        Assert.assertEquals(1, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assert.assertEquals("pkh(keystore1)", wallet.getDefaultPolicy().getMiniscript().getScript());
        Assert.assertTrue(wallet.isValid());
        Assert.assertEquals("3d88d0cf", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals("m/44'/0'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assert.assertEquals("xpub6AuabxJxEnAJbc8iBE2B5n7hxYAZC5xLjpG7oY1kyhMfz5mN13wLRaGPnCyvLo4Ec5aRSa6ZeMPHMUEABpdKxtcPymJpDG5KPEsLGTApGye", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
    }
}
