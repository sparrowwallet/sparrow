package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;
import java.io.InputStream;

public interface MultisigWalletImport extends Import {
    String getWalletImportDescription();
    Wallet importWallet(InputStream inputStream, String password) throws ImportException;
    boolean isEncrypted(File file);
}
