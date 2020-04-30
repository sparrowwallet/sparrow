package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;

public interface KeystoreMnemonicImport extends KeystoreImport {
    Keystore getKeystore(ScriptType scriptType, String[] mnemonicWords, String passphrase) throws ImportException;
}
