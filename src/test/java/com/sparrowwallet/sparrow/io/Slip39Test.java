package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.slip39.Shamir;
import com.sparrowwallet.drongo.wallet.slip39.Slip39MnemonicCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Slip39Test {
    private static final List<String> MNEMONICS = List.of(
            "budget surprise agency romp clay quiet spew favorite purchase thumb observe sympathy acrobat presence unfold focus already tricycle diagnose muscle",
            "budget surprise birthday romp breathe scroll intend humidity aspect plains isolate crystal blimp dragon garbage both kind dance human craft",
            "budget surprise cleanup romp early remove uncover hearing lamp firefly ounce species submit snake phrase genuine tactics mustang desire artist",
            "budget surprise desert romp deny easy senior arena smirk average single smart fatal lecture threaten eyebrow lunar carve indicate romantic",
            "budget surprise email romp academic anxiety sister nylon fake makeup spine breathe window true recall desktop shelter divorce luck screw");

    @AfterEach
    public void tearDown() {
        Network.set(null);
    }

    @Test
    public void importedSeedDecryptsAsTheSharesWithAnyPassphrase() throws Exception {
        Network.set(Network.MAINNET);
        List<List<String>> shares = MNEMONICS.stream().map(mnemonic -> Arrays.asList(mnemonic.split(" "))).toList();
        Keystore keystore = new Slip39().getKeystore(PolicyType.SINGLE_HD, ScriptType.P2WPKH.getDefaultDerivation(), shares, "TREZOR");

        List<String> storedMnemonic = keystore.getSeed().getMnemonicCode();
        for(String passphrase : List.of("TREZOR", "", "another")) {
            byte[] expected = Shamir.combineMnemonics(MNEMONICS, passphrase.getBytes(StandardCharsets.US_ASCII));
            Assertions.assertArrayEquals(expected, Slip39MnemonicCode.INSTANCE.toSeed(storedMnemonic, passphrase), passphrase);
        }
    }
}
