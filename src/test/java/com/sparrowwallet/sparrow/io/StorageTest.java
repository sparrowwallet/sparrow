package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECIESKeyCrypter;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

public class StorageTest extends IoTest {
    @Test
    public void loadWallet() throws IOException {
        ECKey decryptionKey = ECIESKeyCrypter.deriveECKey("pass");
        Wallet wallet = Storage.getStorage().loadWallet(getFile("sparrow-single-wallet"), decryptionKey);
        Assert.assertTrue(wallet.isValid());
    }

    @Test
    public void loadSeedWallet() throws IOException {
        ECKey decryptionKey = ECIESKeyCrypter.deriveECKey("pass");

        Wallet wallet = Storage.getStorage().loadWallet(getFile("sparrow-single-seed-wallet"), decryptionKey);
        Assert.assertTrue(wallet.isValid());

        Assert.assertEquals("testa1", wallet.getName());
        Assert.assertEquals(PolicyType.SINGLE, wallet.getPolicyType());
        Assert.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Assert.assertEquals(1, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assert.assertEquals("pkh(60bcd3a7)", wallet.getDefaultPolicy().getMiniscript().getScript());
        Assert.assertEquals("60bcd3a7", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals("m/84'/0'/0'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assert.assertEquals("xpub6BrhGFTWPd3DQaGP7p5zTQkE5nqVbaRs23HNae8jAoNJYS2NGa9Sgpeqv1dS5ygwD4sQfwqLCk5qXRK45FTgnqHRcrPnts3Qgh78BZrnoMn", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
        Assert.assertEquals("b0e161bff5f589e74b20d9cd260702a6a1e6e1ab3ba4ce764f388dd8f360a1ccdb21099a2f22757ca72f9bde3a34b97a31fb513fb8931c821b0d25798e450b6a57dc106973849ca586b50b2db2840adc", Utils.bytesToHex(wallet.getKeystores().get(0).getSeed().getEncryptedSeedData().getEncryptedBytes()));
        Assert.assertNull(wallet.getKeystores().get(0).getSeed().getSeedBytes());
    }

    @Test
    public void saveWallet() throws IOException {
        ECKey decryptionKey = ECIESKeyCrypter.deriveECKey("pass");
        Wallet wallet = Storage.getStorage().loadWallet(getFile("sparrow-single-wallet"), decryptionKey);
        Assert.assertTrue(wallet.isValid());

        ECKey encyptionKey = ECKey.fromPublicOnly(decryptionKey);
        File tempWallet = File.createTempFile("sparrow", "tmp");
        tempWallet.deleteOnExit();

        ByteArrayOutputStream dummyFileOutputStream = new ByteArrayOutputStream();
        Storage.getStorage().storeWallet(tempWallet, encyptionKey, wallet);

        wallet = Storage.getStorage().loadWallet(tempWallet, decryptionKey);
        Assert.assertTrue(wallet.isValid());
    }
}
