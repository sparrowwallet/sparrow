package com.sparrowwallet.sparrow.external;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.io.InputStream;

public interface KeystoreImport extends Import {
    PolicyType getPolicyType();
    Keystore getKeystore(ScriptType scriptType, InputStream inputStream) throws ImportException;
    String getKeystoreImportDescription();
}
