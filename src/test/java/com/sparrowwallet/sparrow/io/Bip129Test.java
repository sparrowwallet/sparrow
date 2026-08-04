package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Bip129Test extends IoTest {
    @Test
    public void importWallet1() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-1.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals(PolicyType.MULTI_HD, wallet.getPolicyType());
        Assertions.assertEquals(ScriptType.P2WSH, wallet.getScriptType());
        Assertions.assertEquals(2, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assertions.assertEquals(2, wallet.getKeystores().size());
        Assertions.assertEquals("bc1qfagqa83vrv9phj0886rlx6d68zzd7gejtr0ns8xxfeckve27397q6vq47w",
                wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next().getAddress().toString());
    }

    //Test vector from BIP129 (NO_ENCRYPTION with XPUBs, round 2)
    @Test
    public void importWallet2() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-2.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals(ScriptType.P2WSH, wallet.getScriptType());
        Assertions.assertEquals(2, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assertions.assertEquals("bc1qrgc6p3kylfztu06ysl752gwwuekhvtfh9vr7zg43jvu60mutamcsv948ej",
                wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next().getAddress().toString());
    }

    //Test vector from BIP129 (EXTENDED encryption, round 2) - multi() is interpreted as sortedmulti(), but these keys are not in BIP67 order so sorting derives a different first address
    @Test
    public void importWallet3() {
        Bip129 bip129 = new Bip129();
        Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-3.bsms"), null));
    }

    //As above, but with the first address uppercase as bech32 addresses are conventionally QR encoded
    @Test
    public void importWallet4() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-4.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals("bc1qrgc6p3kylfztu06ysl752gwwuekhvtfh9vr7zg43jvu60mutamcsv948ej",
                wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next().getAddress().toString());
    }

    //Sparrow derives the standard receive and change chains only, so a record restricted to any other derivation cannot be honoured
    @Test
    public void importWalletUnsupportedPathRestrictions() {
        Bip129 bip129 = new Bip129();
        Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-5.bsms"), null));
    }

    //Path support is a property of the record, so it is rejected for the derivation rather than for the first address it also omits
    @Test
    public void importWalletUnsupportedPathRestrictionsNoAddress() {
        Bip129 bip129 = new Bip129();
        ImportException e = Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-7.bsms"), null));
        Assertions.assertTrue(e.getCause().getMessage().contains("standard receive and change derivation"));
    }

    //BIP129 requires the first address, and without it the descriptor cannot be verified against the other signers in the quorum
    @Test
    public void importWalletNoFirstAddress() {
        Bip129 bip129 = new Bip129();
        ImportException e = Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-14.bsms"), null));
        Assertions.assertTrue(e.getCause().getMessage().contains("does not provide a first address"));
    }

    //Without path restrictions the descriptor describes a single address, which is still checked against the record
    @Test
    public void importWalletNoPathRestrictionsSingleAddress() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-8.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals(2, wallet.getDefaultPolicy().getNumSignaturesRequired());
    }

    //As above for P2SH-P2WSH, where the keys sit at a child number that is otherwise mistaken for a chain index
    @Test
    public void importWalletNoPathRestrictionsNestedSegwit() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-10.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals(ScriptType.P2SH_P2WSH, wallet.getScriptType());
        Assertions.assertEquals(3, wallet.getKeystores().size());
    }

    //A descriptor fixed to a non-standard chain is rejected even though its first address matches, as that address is not in the wallet derived here
    @Test
    public void importWalletNoPathRestrictionsNonStandardChain() {
        Bip129 bip129 = new Bip129();
        Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-11.bsms"), null));
    }

    //A descriptor fixed to the change chain is on a chain derived here, so it is checked and imported
    @Test
    public void importWalletNoPathRestrictionsChangeChain() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-12.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals("bc1qr47z3tyaep62u4v3tjwj3gre5aqje242g0aarp0gurnltwpswa0svqrv9y",
                wallet.getNode(KeyPurpose.CHANGE).getChildren().stream().filter(node -> node.getIndex() == 3).findFirst().orElseThrow().getAddress().toString());
    }

    //A descriptor fixed at the chain itself rather than an index on it derives an address that is not in the wallet either
    @Test
    public void importWalletNoPathRestrictionsChainDepthOnly() {
        Bip129 bip129 = new Bip129();
        Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-13.bsms"), null));
    }

    @Test
    public void importWalletNoPathRestrictionsAddressMismatch() {
        Bip129 bip129 = new Bip129();
        Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-9.bsms"), null));
    }

    //Only the receive and change chains are derived here, so an unrestricted wildcard descriptor is still checked against the first receive address
    @Test
    public void importWalletNoPathRestrictions() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-6.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals("bc1qrgc6p3kylfztu06ysl752gwwuekhvtfh9vr7zg43jvu60mutamcsv948ej",
                wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next().getAddress().toString());
    }

    //Declaring no path restrictions must not opt the record out of the first address check
    @Test
    public void importWalletNoPathRestrictionsSubstitutedAddress() {
        Bip129 bip129 = new Bip129();
        ImportException e = Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-15.bsms"), null));
        Assertions.assertTrue(e.getCause().getMessage().contains("different set of keys"));
    }

    //Nor must a blank path restrictions line, which is indistinguishable from a truncated file
    @Test
    public void importWalletBlankPathRestrictions() throws ImportException {
        Bip129 bip129 = new Bip129();
        Wallet wallet = bip129.importWallet(getInputStream("bsms/multisig-16.bsms"), null);
        Assertions.assertTrue(wallet.isValid());
    }

    //The first address is parsed before it is compared, so a malformed address is rejected on every path
    @Test
    public void importWalletNoPathRestrictionsMalformedAddress() {
        Bip129 bip129 = new Bip129();
        ImportException e = Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-17.bsms"), null));
        Assertions.assertTrue(e.getCause().getMessage().contains("is not a valid address"));
    }

    @Test
    public void importWalletThresholdZero() {
        Bip129 bip129 = new Bip129();
        Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-threshold-zero.bsms"), null));
    }

    @Test
    public void importWalletAddressMismatch() {
        Bip129 bip129 = new Bip129();
        Assertions.assertThrows(ImportException.class, () -> bip129.importWallet(getInputStream("bsms/multisig-address-mismatch.bsms"), null));
    }
}
