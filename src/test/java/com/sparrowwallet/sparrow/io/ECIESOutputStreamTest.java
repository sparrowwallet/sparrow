package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ECIESOutputStreamTest extends IoTest {
    @Test
    public void encrypt() throws ImportException, ExportException {
        Electrum electrum = new Electrum();
        ECKey decryptionKey = ECKey.createKeyPbkdf2HmacSha512("pass");
        Wallet wallet = electrum.importWallet(new InflaterInputStream(new ECIESInputStream(getInputStream("electrum-encrypted"), decryptionKey)));

        ECKey encyptionKey = ECKey.fromPublicOnly(decryptionKey);
        ByteArrayOutputStream dummyFileOutputStream = new ByteArrayOutputStream();
        electrum.exportWallet(wallet, new DeflaterOutputStream(new ECIESOutputStream(dummyFileOutputStream, encyptionKey)));

        ByteArrayInputStream dummyFileInputStream = new ByteArrayInputStream(dummyFileOutputStream.toByteArray());
        wallet = electrum.importWallet(new InflaterInputStream(new ECIESInputStream(dummyFileInputStream, decryptionKey)));

        Assert.assertEquals(PolicyType.SINGLE, wallet.getPolicyType());
        Assert.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Assert.assertEquals(1, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assert.assertEquals("pkh(electrum05aba071)", wallet.getDefaultPolicy().getMiniscript().getScript());
        Assert.assertEquals("05aba071", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals("m/0'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assert.assertEquals("xpub67vv394epQsLhdjNGx7dfgURicP7XwBMuHPTVAMdXcXhDuC9VP8SqVvh2cYqKWm9xoUd6YynWK8JzRcXpmeuZFRH7i1kt8fR9GXoJSiHk1E", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
        Assert.assertTrue(wallet.isValid());
    }
}
