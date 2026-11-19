package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.registry.*;
import com.sparrowwallet.hummingbird.registry.pathcomponent.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class QRScanDialogTest {
    //BIP32 test vector 1 at m/0', a child of the master key with fingerprint 3442193e
    private static final String XPUB = "xpub68Gmy5EdvgibQVfPdqkBBCHxA5htiqg55crXYuXoQRKfDBFA1WEjWgP6LHhwBZeNK1VTsfTFUHCdrfp1bgwQ9xv5ski8PX9rL2dZXvgGDnw";
    private static final String XPRV = "xprv9uHRZZhk6KAJC1avXpDAp4MDc3sQKNxDiPvvkX8Br5ngLNv1TxvUxt4cV1rGL5hj6KCesnDYUhd7oWgT11eZG7XnxHrnYeSvkzY7d2bhkJ7";
    private static final String ORIGIN = "[3442193e/0']";
    //The same vector at m/0'/1
    private static final String SECOND_XPUB = "xpub6ASuArnXKPbfEwhqN6e3mwBcDTgzisQN1wXN9BJcM47sSikHjJf3UFHKkNAWbWMiGj7Wf5uMash7SyYq527Hqck2AxYysAA7xmALppuCkwQ";

    private static CryptoKeypath origin() {
        return new CryptoKeypath(List.of(new IndexPathComponent(0, true)), Utils.hexToBytes("3442193e"));
    }

    private static CryptoKeypath children(PathComponent... components) {
        return new CryptoKeypath(List.of(components), null);
    }

    private static PairPathComponent receiveAndChange() {
        return new PairPathComponent(new IndexPathComponent(0, false), new IndexPathComponent(1, false));
    }

    private static CryptoHDKey hdKey(CryptoKeypath children) {
        ExtendedKey xpub = ExtendedKey.fromDescriptor(XPUB);
        return new CryptoHDKey(false, xpub.getKey().getPubKey(), xpub.getKey().getChainCode(), null, origin(), children, xpub.getParentFingerprint());
    }

    private static OutputDescriptor fromCryptoOutput(CryptoHDKey hdKey) {
        return QRScanDialog.QRResultListener.getOutputDescriptor(new CryptoOutput(List.of(ScriptExpression.WITNESS_PUBLIC_KEY_HASH), hdKey));
    }

    @Test
    public void cryptoOutputChildrenAreAppliedAsInText() {
        OutputDescriptor scanned = fromCryptoOutput(hdKey(children(new IndexPathComponent(7, false), receiveAndChange(), new WildcardPathComponent(false))));
        OutputDescriptor text = OutputDescriptor.getOutputDescriptor("wpkh(" + ORIGIN + XPUB + "/7/<0;1>/*)");
        Assertions.assertEquals(text.toString(), scanned.toString());
        Assertions.assertNotEquals(XPUB, scanned.getExtendedPublicKeys().iterator().next().toString());
    }

    @Test
    public void cryptoOutputWithoutChildrenIsUnchanged() {
        OutputDescriptor scanned = fromCryptoOutput(hdKey(null));
        Assertions.assertEquals(XPUB, scanned.getExtendedPublicKeys().iterator().next().toString());
    }

    @Test
    public void cryptoOutputWithNonStandardChildrenIsLeftForTheConsumerToCheck() {
        OutputDescriptor scanned = fromCryptoOutput(hdKey(children(new IndexPathComponent(2, false), new WildcardPathComponent(false))));
        Assertions.assertFalse(scanned.hasStandardChildDerivations());
        Assertions.assertThrows(IllegalStateException.class, () -> fromCryptoOutput(hdKey(children(new RangePathComponent(0, 5, false)))));
    }

    @Test
    public void accountOutputThatCannotBeConvertedIsSkipped() {
        CryptoOutput unconvertible = new CryptoOutput(List.of(ScriptExpression.WITNESS_PUBLIC_KEY_HASH), hdKey(children(new RangePathComponent(0, 5, false))));
        CryptoOutput lone = new CryptoOutput(List.of(ScriptExpression.PUBLIC_KEY_HASH), hdKey(children(new WildcardPathComponent(false))));
        CryptoAccount account = new CryptoAccount(Utils.hexToBytes("3442193e"), List.of(unconvertible, lone));

        List<Wallet> wallets = QRScanDialog.QRResultListener.getWallets(account);
        Assertions.assertEquals(1, wallets.size());
        Assertions.assertEquals(ScriptType.P2PKH, wallets.getFirst().getScriptType());
        Assertions.assertEquals(XPUB, wallets.getFirst().getKeystores().getFirst().getExtendedPublicKey().toString());

        CryptoAccount allUnconvertible = new CryptoAccount(Utils.hexToBytes("3442193e"), List.of(unconvertible));
        Assertions.assertThrows(IllegalStateException.class, () -> QRScanDialog.QRResultListener.getWallets(allUnconvertible));
    }

    @Test
    public void cryptoOutputPrivateKeyIsReducedToItsPublicKey() {
        ExtendedKey xprv = ExtendedKey.fromDescriptor(XPRV);
        byte[] keyData = new byte[33];
        System.arraycopy(xprv.getKey().getPrivKeyBytes(), 0, keyData, 1, 32);
        CryptoHDKey privateHdKey = new CryptoHDKey(true, keyData, xprv.getKey().getChainCode(), null, origin(), null, Utils.hexToBytes("3442193e"));

        OutputDescriptor scanned = fromCryptoOutput(privateHdKey);
        ExtendedKey key = scanned.getExtendedPublicKeys().iterator().next();
        Assertions.assertTrue(key.getKey().isPubKeyOnly());
        Assertions.assertEquals(XPUB, key.toString());
    }

    @Test
    public void urOutputDescriptorChildrenAreAppliedAsInText() {
        ExtendedKey xpub = ExtendedKey.fromDescriptor(XPUB);
        URKeypath origin = new URKeypath(List.of(new IndexPathComponent(0, true)), Utils.hexToBytes("3442193e"));
        URKeypath children = new URKeypath(List.of(new IndexPathComponent(7, false), receiveAndChange(), new WildcardPathComponent(false)), null);
        URHDKey urhdKey = new URHDKey(false, xpub.getKey().getPubKey(), xpub.getKey().getChainCode(), null, origin, children, xpub.getParentFingerprint());

        OutputDescriptor scanned = QRScanDialog.QRResultListener.getOutputDescriptor(new UROutputDescriptor("wpkh(@0)", new ArrayList<>(List.of(urhdKey))));
        OutputDescriptor text = OutputDescriptor.getOutputDescriptor("wpkh(" + ORIGIN + XPUB + "/7/<0;1>/*)");
        Assertions.assertEquals(text.toString(), scanned.toString());
    }

    private static URHDKey urHdKey(String xpubString, List<PathComponent> origin, String name) {
        ExtendedKey xpub = ExtendedKey.fromDescriptor(xpubString);
        URKeypath urOrigin = new URKeypath(origin, Utils.hexToBytes("3442193e"));
        URKeypath children = new URKeypath(List.of(new IndexPathComponent(7, false), receiveAndChange(), new WildcardPathComponent(false)), null);
        return new URHDKey(false, xpub.getKey().getPubKey(), xpub.getKey().getChainCode(), null, urOrigin, children, xpub.getParentFingerprint(), name, null);
    }

    @Test
    public void cryptoOutputKeyKeepsItsNameWhenChildrenAreApplied() {
        ExtendedKey xpub = ExtendedKey.fromDescriptor(XPUB);
        CryptoKeypath children = children(new IndexPathComponent(7, false), receiveAndChange(), new WildcardPathComponent(false));
        CryptoHDKey named = new CryptoHDKey(false, xpub.getKey().getPubKey(), xpub.getKey().getChainCode(), null, origin(), children, xpub.getParentFingerprint(), "Device", null);

        OutputDescriptor scanned = fromCryptoOutput(named);
        Assertions.assertEquals("Device", scanned.getExtendedPublicKeyLabel(scanned.getExtendedPublicKeys().iterator().next()));
    }

    @Test
    public void urOutputDescriptorKeysKeepTheirNamesInSourceOrder() {
        URHDKey first = urHdKey(XPUB, List.of(new IndexPathComponent(0, true)), "First");
        URHDKey second = urHdKey(SECOND_XPUB, List.of(new IndexPathComponent(0, true), new IndexPathComponent(1, false)), "Second");

        OutputDescriptor scanned = QRScanDialog.QRResultListener.getOutputDescriptor(new UROutputDescriptor("wsh(sortedmulti(1,@1,@0))", new ArrayList<>(List.of(first, second))));
        for(ExtendedKey key : scanned.getExtendedPublicKeys()) {
            String expected = scanned.getKeyDerivation(key).getDerivationPath().startsWith("m/0'/1/") ? "Second" : "First";
            Assertions.assertEquals(expected, scanned.getExtendedPublicKeyLabel(key));
        }
    }

    @Test
    public void cryptoOutputCosignersKeepTheirNamesInEitherScanOrder() {
        CryptoHDKey first = namedHdKey(XPUB, List.of(new IndexPathComponent(0, true)), "First");
        CryptoHDKey second = namedHdKey(SECOND_XPUB, List.of(new IndexPathComponent(0, true), new IndexPathComponent(1, false)), "Second");

        for(List<CryptoHDKey> scanOrder : List.of(List.of(first, second), List.of(second, first))) {
            CryptoOutput cryptoOutput = new CryptoOutput(List.of(ScriptExpression.WITNESS_SCRIPT_HASH, ScriptExpression.SORTED_MULTISIG), new MultiKey(1, null, scanOrder));
            OutputDescriptor scanned = QRScanDialog.QRResultListener.getOutputDescriptor(cryptoOutput);
            Assertions.assertEquals(2, scanned.getExtendedPublicKeys().size());
            for(ExtendedKey key : scanned.getExtendedPublicKeys()) {
                String expected = scanned.getKeyDerivation(key).getDerivationPath().startsWith("m/0'/1/") ? "Second" : "First";
                Assertions.assertEquals(expected, scanned.getExtendedPublicKeyLabel(key));
            }
        }
    }

    private static CryptoHDKey namedHdKey(String xpubString, List<PathComponent> origin, String name) {
        ExtendedKey xpub = ExtendedKey.fromDescriptor(xpubString);
        CryptoKeypath cryptoOrigin = new CryptoKeypath(origin, Utils.hexToBytes("3442193e"));
        CryptoKeypath children = children(new IndexPathComponent(7, false), receiveAndChange(), new WildcardPathComponent(false));
        return new CryptoHDKey(false, xpub.getKey().getPubKey(), xpub.getKey().getChainCode(), null, cryptoOrigin, children, xpub.getParentFingerprint(), name, null);
    }
}
