package com.sparrowwallet.sparrow.paynym;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PayNymServiceTest {
    static Wallet getWallet() throws Exception {
        DeterministicSeed seed = new DeterministicSeed("reward upper indicate eight swift arch injury crystal super wrestle already dentist", "", 0, DeterministicSeed.Type.BIP39);
        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2WPKH);
        wallet.getKeystores().add(Keystore.fromSeed(seed, PolicyType.SINGLE_HD, List.of(new ChildNumber(84, true), ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED)));
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE_HD, ScriptType.P2WPKH, wallet.getKeystores(), 1));
        return wallet;
    }

    @Test
    public void signsAuthToken() throws Exception {
        assertFalse(PayNymService.getSignature(getWallet(), "a2b4c6d8e0f1").isEmpty());
    }

    @Test
    public void refusesTokenInAnotherFormat() throws Exception {
        Wallet wallet = getWallet();
        assertThrows(IllegalArgumentException.class, () -> PayNymService.getSignature(wallet, "auth47://a2b4c6d8e0f1?r=https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> PayNymService.getSignature(wallet, "AUTH47:a2b4c6d8e0f1"));
        assertThrows(IllegalArgumentException.class, () -> PayNymService.getSignature(wallet, "https://example.com"));
    }
}
