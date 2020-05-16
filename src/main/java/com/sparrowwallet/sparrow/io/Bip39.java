package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Bip39MnemonicCode;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.util.List;

public class Bip39 implements KeystoreMnemonicImport {
    @Override
    public String getName() {
        return "Mnemonic Words (BIP39)";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SEED;
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import your 12 to 24 word mnemonic and optional passphrase";
    }

    @Override
    public Keystore getKeystore(List<ChildNumber> derivation, List<String> mnemonicWords, String passphrase) throws ImportException {
        try {
            Bip39MnemonicCode.INSTANCE.check(mnemonicWords);
            DeterministicSeed seed = new DeterministicSeed(mnemonicWords, passphrase, System.currentTimeMillis(), DeterministicSeed.Type.BIP39);
            return Keystore.fromSeed(seed, derivation);
        } catch (Exception e) {
            throw new ImportException(e);
        }
    }
}
