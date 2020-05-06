package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.util.List;

public interface KeystoreMnemonicImport extends KeystoreImport {
    Keystore getKeystore(ScriptType scriptType, List<String> mnemonicWords, String passphrase) throws ImportException;
}
