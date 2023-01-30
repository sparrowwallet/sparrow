package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import javafx.beans.property.StringProperty;

import java.util.List;

public interface KeystoreCardImport extends CardImport {
    Keystore getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty) throws ImportException;
    String getKeystoreImportDescription(int account);
}
