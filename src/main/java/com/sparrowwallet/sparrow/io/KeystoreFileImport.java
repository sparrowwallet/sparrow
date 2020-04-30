package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.io.InputStream;

public interface KeystoreFileImport extends KeystoreImport {
    Keystore getKeystore(ScriptType scriptType, InputStream inputStream) throws ImportException;
}
