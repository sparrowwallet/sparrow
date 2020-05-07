package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.util.List;

public interface KeystoreMnemonicImport extends KeystoreImport {
    Keystore getKeystore(List<ChildNumber> derivation, List<String> mnemonicWords, String passphrase) throws ImportException;
}
