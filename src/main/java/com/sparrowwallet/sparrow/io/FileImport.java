package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;

import java.io.File;

public interface FileImport extends ImportExport {
    boolean isEncrypted(File file);

    default ExtendedKey getExtendedPublicKey(String extendedPublicKey) throws ImportException {
        ExtendedKey extendedKey = ExtendedKey.fromDescriptor(extendedPublicKey);
        if(!extendedKey.getKey().isPubKeyOnly()) {
            throw new ImportException("The file contains an extended private key where an extended public key was expected.");
        }

        return extendedKey;
    }
}
