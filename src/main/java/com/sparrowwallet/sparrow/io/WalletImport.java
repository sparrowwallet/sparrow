package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;
import java.io.InputStream;

public interface WalletImport extends Import, FileImport {
    String getWalletImportDescription();
    Wallet importWallet(InputStream inputStream, String password) throws ImportException;
}
