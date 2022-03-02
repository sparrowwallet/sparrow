package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

public class StorageTest extends IoTest {
    @Test
    public void loadWallet() throws IOException, MnemonicException, StorageException {
        Storage storage = new Storage(getFile("sparrow-single-wallet"));
        Wallet wallet = storage.loadEncryptedWallet("pass").getWallet();
        Assert.assertTrue(wallet.isValid());
    }

    @Test
    public void loadSeedWallet() throws IOException, MnemonicException, StorageException {
        Storage storage = new Storage(getFile("sparrow-single-seed-wallet"));
        WalletAndKey walletAndKey = storage.loadEncryptedWallet("pass");
        Wallet wallet = walletAndKey.getWallet();
        Wallet copy = wallet.copy();
        copy.decrypt(walletAndKey.getKey());

        for(int i = 0; i < wallet.getKeystores().size(); i++) {
            Keystore keystore = wallet.getKeystores().get(i);
            if(keystore.hasSeed()) {
                Keystore copyKeystore = copy.getKeystores().get(i);
                Keystore derivedKeystore = Keystore.fromSeed(copyKeystore.getSeed(), copyKeystore.getKeyDerivation().getDerivation());
                keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                keystore.getSeed().setPassphrase(copyKeystore.getSeed().getPassphrase());
                copyKeystore.getSeed().clear();
            }
        }

        Assert.assertTrue(wallet.isValid());

        Assert.assertEquals("testd2", wallet.getName());
        Assert.assertEquals(PolicyType.SINGLE, wallet.getPolicyType());
        Assert.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Assert.assertEquals(1, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assert.assertEquals("pkh(60bcd3a7)", wallet.getDefaultPolicy().getMiniscript().getScript());
        Assert.assertEquals("60bcd3a7", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals("m/84'/0'/3'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assert.assertEquals("xpub6BrhGFTWPd3DXo8s2BPxHHzCmBCyj8QvamcEUaq8EDwnwXpvvcU9LzpJqENHcqHkqwTn2vPhynGVoEqj3PAB3NxnYZrvCsSfoCniJKaggdy", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
        Assert.assertEquals("af6ebd81714c301c3a71fe11a7a9c99ccef4b33d4b36582220767bfa92768a2aa040f88b015b2465f8075a8b9dbf892a7d6e6c49932109f2cbc05ba0bd7f355fbcc34c237f71be5fb4dd7f8184e44cb0", Utils.bytesToHex(wallet.getKeystores().get(0).getSeed().getEncryptedData().getEncryptedBytes()));
        Assert.assertNull(wallet.getKeystores().get(0).getSeed().getMnemonicCode());
        Assert.assertEquals("bc1q2mkrttcuzryrdyn9vtu3nfnt3jlngwn476ktus", wallet.getFreshNode(KeyPurpose.RECEIVE).getAddress().toString());
    }

    @Test
    public void multipleLoadTest() throws IOException, MnemonicException, StorageException {
        for(int i = 0; i < 5; i++) {
            loadSeedWallet();
        }
    }

    @Test
    public void saveWallet() throws IOException, MnemonicException, StorageException {
        Storage storage = new Storage(getFile("sparrow-single-wallet"));
        Wallet wallet = storage.loadEncryptedWallet("pass").getWallet();
        Assert.assertTrue(wallet.isValid());

        File tempWallet = File.createTempFile("sparrow", "tmp");
        tempWallet.deleteOnExit();

        Storage tempStorage = new Storage(tempWallet);
        tempStorage.setKeyDeriver(storage.getKeyDeriver());
        tempStorage.setEncryptionPubKey(storage.getEncryptionPubKey());
        tempStorage.saveWallet(wallet);

        Storage temp2Storage = new Storage(tempWallet);
        wallet = temp2Storage.loadEncryptedWallet("pass").getWallet();
        Assert.assertTrue(wallet.isValid());
    }
}
