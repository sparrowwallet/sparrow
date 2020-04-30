package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.io.File;
import java.io.InputStream;

public interface KeystoreFileImport extends KeystoreImport {
    boolean isEncrypted(File file);
    Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException;
}
