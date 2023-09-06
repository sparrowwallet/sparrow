package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.InputStream;

public interface WalletImport extends FileImport {
    String getWalletImportDescription();
    Wallet importWallet(InputStream inputStream, String password) throws ImportException;
    boolean isWalletImportScannable();
    default boolean isWalletImportFileFormatAvailable() {
        return true;
    }
}
