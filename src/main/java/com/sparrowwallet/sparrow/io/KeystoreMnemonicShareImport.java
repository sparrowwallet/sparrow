package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.util.List;

public interface KeystoreMnemonicShareImport extends KeystoreImport {
    Keystore getKeystore(List<ChildNumber> derivation, List<List<String>> mnemonicShares, String passphrase) throws ImportException;
}
