package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class StorageTest extends IoTest {
    @Test
    public void loadWallet() throws IOException {
        ECKey decryptionKey = ECKey.createKeyPbkdf2HmacSha512("pass");
        Wallet wallet = Storage.getStorage().loadWallet(getFile("sparrow-single-wallet"), decryptionKey);
        Assert.assertTrue(wallet.isValid());
    }

    @Test
    public void saveWallet() throws IOException {
        ECKey decryptionKey = ECKey.createKeyPbkdf2HmacSha512("pass");
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
