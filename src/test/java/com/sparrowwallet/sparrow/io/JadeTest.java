package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JadeTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        Jade jade = new Jade();
        Keystore keystore = jade.getKeystore(PolicyType.SINGLE_HD, ScriptType.P2WPKH, getInputStream("jade-keystore.txt"), null);

        Assertions.assertEquals("Jade", keystore.getLabel());
        Assertions.assertEquals(WalletModel.JADE, keystore.getWalletModel());
        Assertions.assertEquals(KeystoreSource.HW_AIRGAPPED, keystore.getSource());
        Assertions.assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        Assertions.assertEquals("73c5da0a", keystore.getKeyDerivation().getMasterFingerprint());
        Assertions.assertEquals(ExtendedKey.fromDescriptor("zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"), keystore.getExtendedPublicKey());
        Assertions.assertTrue(keystore.isValid());
    }

    @Test
    public void testImportScriptTypeMismatch() {
        Jade jade = new Jade();
        ImportException scriptTypeException = Assertions.assertThrows(ImportException.class, () -> jade.getKeystore(PolicyType.SINGLE_HD, ScriptType.P2TR, getInputStream("jade-keystore.txt"), null));
        Assertions.assertTrue(scriptTypeException.getCause().getMessage().startsWith("The exported xpub is for Native Segwit (P2WPKH), not Taproot (P2TR)."));
    }

    @Test
    public void testImportSilentPayments() {
        Jade jade = new Jade();
        ImportException silentPaymentsException = Assertions.assertThrows(ImportException.class, () -> jade.getKeystore(PolicyType.SINGLE_SP, ScriptType.P2TR, getInputStream("jade-keystore.txt"), null));
        Assertions.assertEquals("Export does not contain the spscan value for silent payments", silentPaymentsException.getCause().getMessage());
    }
}
