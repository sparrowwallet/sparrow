package com.sparrowwallet.sparrow.io;

import com.google.common.io.ByteStreams;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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

    @Test
    public void testExport() throws ExportException, IOException {
        OutputDescriptor walletDescriptor = OutputDescriptor.getOutputDescriptor("wsh(sortedmulti(2,[7fd1bbf4/48h/0h/0h/2h]xpub6DnVFCXjZKhSAJw1oGzksdc1CtMxHxqG6DgNSjZHsymMSgcNEb2c3bz5N2bBMEEUFos98CeAWbh1pTMBcJrsKW63icdAQNGT6Aqv1WWrkxg,[8ff26349/48h/0h/0h/2h]xpub6ErPooPdSeBoXVZocBe8EWF9GXjFuV52kme35p4MtrP2SAFdUmgTJM1urrJzSuA44izrEuiQNNdmWEVRaBJcBDcPpnLBR8tP2Pcu2EiyeHu,[ff3305c2/48h/0h/0h/2h]xpub6Dpndp2xurqbfSGhxKVXzk3nJZgah3PdD3qD11KyPicYYBatRxfxqoN7s9tnWKXaz7zhyVqcvnJyak7BVKonW2wTXHd1zNDxJAu8jcxF59j))");
        Wallet wallet = walletDescriptor.toWallet();
        wallet.setName("Sparrow Multisig");

        SpecterDIY specterDIY = new SpecterDIY();
        byte[] walletBytes = ByteStreams.toByteArray(getInputStream("specter-diy-export.txt"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        specterDIY.exportWallet(wallet, baos);
        String original = new String(walletBytes);
        String exported = new String(baos.toByteArray());

        Assert.assertEquals(original, exported);
    }
}
