package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Bip39Calculator;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.util.List;

public class Bip39 implements KeystoreMnemonicImport {

    @Override
    public Keystore getKeystore(ScriptType scriptType, List<String> mnemonicWords, String passphrase) throws ImportException {
        Bip39Calculator bip39Calculator = new Bip39Calculator();

        return null;
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SPARROW;
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import your 12 to 24 word mnemonic and optional passphrase";
    }

    @Override
    public String getName() {
        return null;
    }
}
