package com.sparrowwallet.sparrow.io;

import com.google.common.io.ByteStreams;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public class CaravanMultisigTest extends IoTest {
    @Test
    public void importWallet1() throws ImportException {
        CaravanMultisig ccMultisig = new CaravanMultisig();
        Wallet wallet = ccMultisig.importWallet(getInputStream("caravan-multisig-export-1.json"), null);
        Assert.assertEquals("Test Wallet", wallet.getName());
        Assert.assertEquals(PolicyType.MULTI, wallet.getPolicyType());
        Assert.assertEquals(ScriptType.P2WSH, wallet.getScriptType());
        Assert.assertEquals(2, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assert.assertEquals("wsh(sortedmulti(2,mercury,venus,earth))", wallet.getDefaultPolicy().getMiniscript().getScript().toLowerCase(Locale.ROOT));
        Assert.assertTrue(wallet.isValid());
        Assert.assertEquals("8188029f", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals("m/48'/0'/0'/2'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assert.assertEquals(WalletModel.TREZOR_1, wallet.getKeystores().get(0).getWalletModel());
        Assert.assertEquals("xpub6EMVvcTUbaABdaPLaVWE72CjcN72URa5pKK1knrKLz1hKaDwUkgddc3832a8MHEpLyuow7MfjMRomt2iMtwPH4pWrFLft4JsquHjeZfKsYp", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
    }

    @Test
    public void exportWallet1() throws ImportException, ExportException, IOException {
        CaravanMultisig ccMultisig = new CaravanMultisig();
        byte[] walletBytes = ByteStreams.toByteArray(getInputStream("caravan-multisig-export-1.json"));
        Wallet wallet = ccMultisig.importWallet(new ByteArrayInputStream(walletBytes), null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ccMultisig.exportWallet(wallet, baos);
        byte[] exportedBytes = baos.toByteArray();
        String original = new String(walletBytes);
        String exported = new String(exportedBytes);
        Assert.assertEquals(original, exported);
    }
}
