package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;

import java.util.List;

public interface KeystoreXprvImport extends KeystoreImport {
    Keystore getKeystore(List<ChildNumber> derivation, ExtendedKey xprv) throws ImportException;
}
