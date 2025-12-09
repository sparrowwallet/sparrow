package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.util.List;

public interface KeystoreCodexImport extends KeystoreImport {
    Keystore getKeystore(List<ChildNumber> derivation, String secretShare) throws ImportException;
}
