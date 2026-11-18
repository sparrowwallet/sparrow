package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class DescriptorTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        Descriptor descriptor = new Descriptor();
        Wallet wallet = descriptor.importWallet(getInputStream("descriptor-receive.txt"), null);

        Assertions.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Keystore keystore = wallet.getKeystores().getFirst();
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("a262308d", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testImportMultipath() throws ImportException {
        Descriptor descriptor = new Descriptor();
        Wallet wallet = descriptor.importWallet(getInputStream("descriptor-multipath.txt"), null);

        Assertions.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Keystore keystore = wallet.getKeystores().getFirst();
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("a262308d", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testImportSeparateDescriptors() throws ImportException {
        Descriptor descriptor = new Descriptor();
        Wallet wallet = descriptor.importWallet(getInputStream("descriptor-receive-change1.txt"), null);

        Assertions.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Keystore keystore = wallet.getKeystores().getFirst();
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("a262308d", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testExport() throws ImportException, ExportException {
        Descriptor descriptor = new Descriptor();
        Wallet wallet = descriptor.importWallet(getInputStream("descriptor-multipath.txt"), null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        descriptor.exportWallet(wallet, baos, null);
        String export = baos.toString();

        Assertions.assertTrue(export.contains("wpkh([a262308d/84h/0h/0h]xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz/<0;1>/*)#cpx4ean7"));
        Assertions.assertTrue(export.contains("wpkh([a262308d/84h/0h/0h]xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz/0/*)#s5x06kda"));
        Assertions.assertTrue(export.contains("wpkh([a262308d/84h/0h/0h]xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz/1/*)#pqrw8ra9"));
    }

    @Test
    public void testImportExport() throws ImportException, ExportException {
        Descriptor descriptor = new Descriptor();
        Wallet wallet = descriptor.importWallet(getInputStream("descriptor-multipath.txt"), null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        descriptor.exportWallet(wallet, baos, null);

        Wallet reimported = descriptor.importWallet(new ByteArrayInputStream(baos.toByteArray()), null);

        Assertions.assertEquals(wallet.getScriptType(), reimported.getScriptType());
        Keystore keystore = wallet.getKeystores().getFirst();
        Keystore reimportedKeystore = reimported.getKeystores().getFirst();
        Assertions.assertEquals(keystore.getKeyDerivation().getDerivationPath(), reimportedKeystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals(keystore.getKeyDerivation().getMasterFingerprint(), reimportedKeystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(keystore.getExtendedPublicKey(), reimportedKeystore.getExtendedPublicKey());
        Assertions.assertTrue(reimportedKeystore.isValid());
    }

    @Test
    public void testImportLabelled() throws ImportException {
        Descriptor descriptor = new Descriptor();
        Wallet wallet = descriptor.importWallet(getInputStream("descriptor-labelled.txt"), null);

        Assertions.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Keystore keystore = wallet.getKeystores().getFirst();
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("a262308d", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testImportSeparateDescriptorsNoBlankLine() throws ImportException {
        Descriptor descriptor = new Descriptor();
        Wallet wallet = descriptor.importWallet(getInputStream("descriptor-receive-change2.txt"), null);

        Assertions.assertEquals(ScriptType.P2WPKH, wallet.getScriptType());
        Keystore keystore = wallet.getKeystores().getFirst();
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("a262308d", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("xpub6DM7CYgaTMdMbhTcLTUWmNUE5WLXK5hx8ZMa4sRw8qYJPqtqKYiKnwsmT8A6AijDVAUZRivdBnXdR8QE7Y9vVnqvzPL3fXCmu1WtCRLdAoz"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testImportWrappedMultisigDescriptor() throws ImportException {
        String text = "wsh(sortedmulti(2,[04fefef0/48h/0h/0h/2h]xpub661MyMwAqRbcFW31YEwpkMuc5THy2PSt5bDMsktWQcFF8syAmRUapSCGu8ED9W6oDMSgv6Zz8idoc4a6mr8BDzTJY47LJhkJ8UB7WEGuduB/0/*,\n"
                + "[04ba1ef0/48h/0h/0h/2h]xpub69H7F5d8KSRgmmdJg2KhpAK8SR3DjMwAdkxj3ZuxV27CprR9LgpeyGmXUbC6wb7ERfvrnKZjXoUmmDznezpbZb7ap6r1D3tgFxHmwMkQTPH/0/*,\n"
                + "[04fefef1/48h/0h/0h/2h]xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/0/*))\n";
        Wallet wallet = new Descriptor().importWallet(new ByteArrayInputStream(text.getBytes()), null);

        Assertions.assertEquals(3, wallet.getKeystores().size());
        Assertions.assertEquals(2, wallet.getDefaultPolicy().getNumSignaturesRequired());
    }
}
