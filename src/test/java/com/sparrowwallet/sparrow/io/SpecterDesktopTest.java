package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.Assert;
import org.junit.Test;

import java.util.Locale;

public class SpecterDesktopTest extends IoTest {
    @Test
    public void testImport() throws ImportException {
        SpecterDesktop specterDesktop = new SpecterDesktop();
        Wallet wallet = specterDesktop.importWallet(getInputStream("specter-wallet.json"), null);

        Assert.assertEquals(PolicyType.SINGLE, wallet.getPolicyType());
        Assert.assertEquals(ScriptType.P2SH_P2WPKH, wallet.getScriptType());
        Assert.assertEquals(1, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assert.assertEquals("sh(wpkh(keystore1))", wallet.getDefaultPolicy().getMiniscript().getScript().toLowerCase(Locale.ROOT));
        Assert.assertEquals("4df18faa", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals("m/49'/0'/0'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assert.assertEquals("xpub6BgwyseZdeGJj2vB3FPHSGPxR1LLkr8AsAJqedrgjwBXKXXVWkH31fhwtQXgrM7uMrWjLwXhuDhhenNAh5eBdUSjrHkrKfaXutcJdAfgQ8D", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
        Assert.assertTrue(wallet.isValid());
    }

    @Test
    public void testMultisigImport() throws ImportException {
        SpecterDesktop specterDesktop = new SpecterDesktop();
        Wallet wallet = specterDesktop.importWallet(getInputStream("specter-multisig-wallet.json"), null);

        Assert.assertEquals(PolicyType.MULTI, wallet.getPolicyType());
        Assert.assertEquals(ScriptType.P2WSH, wallet.getScriptType());
        Assert.assertEquals(3, wallet.getDefaultPolicy().getNumSignaturesRequired());
        Assert.assertEquals("wsh(sortedmulti(3,keystore1,keystore2,keystore3,keystore4))", wallet.getDefaultPolicy().getMiniscript().getScript().toLowerCase(Locale.ROOT));
        Assert.assertEquals("ca9a2b19", wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint());
        Assert.assertEquals("m/48'/0'/0'/2'", wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath());
        Assert.assertEquals("xpub6EhbRDNhmMX863W8RujJyAMw1vtM4MHXnsk14paK1ZBEH75k44gWqfaraXCrzg6w9pzC2yLc28vAdUfpB9ShuEB1HA9xMs6BjmRi4PKbt1K", wallet.getKeystores().get(0).getExtendedPublicKey().toString());
        Assert.assertTrue(wallet.isValid());
    }
}
