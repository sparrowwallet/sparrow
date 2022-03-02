package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.*;

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
        return "Generate or import your 12 to 24 word mnemonic and optional passphrase.";
    }

    @Override
    public Keystore getKeystore(List<ChildNumber> derivation, List<String> mnemonicWords, String passphrase) throws ImportException {
        try {
            Bip39MnemonicCode.INSTANCE.check(mnemonicWords);
            DeterministicSeed seed = new DeterministicSeed(mnemonicWords, passphrase, System.currentTimeMillis(), DeterministicSeed.Type.BIP39);
            return Keystore.fromSeed(seed, derivation);
        } catch (Exception e) {
            try {
                ElectrumMnemonicCode.INSTANCE.check(mnemonicWords);
                throw new ImportException(new MnemonicException.MnemonicTypeException(DeterministicSeed.Type.ELECTRUM));
            } catch(Exception ex) {
                if(ex instanceof ImportException && ex.getCause() instanceof MnemonicException.MnemonicTypeException) {
                    throw (ImportException)ex;
                }
            }

            throw new ImportException(e);
        }
    }
}
