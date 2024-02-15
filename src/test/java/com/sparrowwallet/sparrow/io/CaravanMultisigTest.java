package com.sparrowwallet.sparrow.io;

import com.google.common.io.ByteStreams;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public class CaravanMultisigTest extends IoTest {
    @Test
    public void importWallet1() throws ImportException {
        CaravanMultisig ccMultisig = new CaravanMultisig();
        Wallet wallet = ccMultisig.importWallet(getInputStream("caravan-multisig-export-1.json"), null);
        Assertions.assertEquals("Test Wallet", wallet.getName());
        Assertions.assertEquals(PolicyType.MULTI, wallet.getPolicyType());
        Assertions.assertEquals(ScriptType.P2WSH, wallet.getScriptType());
        Assertions.assertEquals(2, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assertions.assertEquals("wsh(sortedmulti(2,mercury,venus,earth))", wallet.getDefaultPolicy().getMiniscript().getScript().toLowerCase(Locale.ROOT));
        Assertions.assertTrue(wallet.isValid());
        Assertions.assertEquals("8188029f", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals("m/48'/0'/0'/2'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assertions.assertEquals(WalletModel.TREZOR_1, wallet.getKeystores().get(0).getWalletModel());
        Assertions.assertEquals("xpub6EMVvcTUbaABdaPLaVWE72CjcN72URa5pKK1knrKLz1hKaDwUkgddc3832a8MHEpLyuow7MfjMRomt2iMtwPH4pWrFLft4JsquHjeZfKsYp", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
    }

    @Test
    public void exportWallet1() throws ImportException, ExportException, IOException {
        CaravanMultisig ccMultisig = new CaravanMultisig();
        byte[] walletBytes = ByteStreams.toByteArray(getInputStream("caravan-multisig-export-1.json"));
        Wallet wallet = ccMultisig.importWallet(new ByteArrayInputStream(walletBytes), null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ccMultisig.exportWallet(wallet, baos, null);
        byte[] exportedBytes = baos.toByteArray();
        String original = new String(walletBytes);
        String exported = new String(exportedBytes);
        Assertions.assertEquals(original, exported);
    }
}
