package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECIESKeyCrypter;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.MnemonicException;
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
    public void loadSeedWallet() throws IOException, MnemonicException {
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
        Assert.assertEquals("a48767d6b58732a0cad17ed93e23022ec603a177e75461f2aed994713fbbe532b61f6c0758a8aedcf9b2b8102c01c6f3e3e212ca06f13644d4ac8dad66556e164b7eaf79d0b42eadecee8b735e97fc0a", Utils.bytesToHex(wallet.getKeystores().get(0).getSeed().getEncryptedData().getEncryptedBytes()));
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
