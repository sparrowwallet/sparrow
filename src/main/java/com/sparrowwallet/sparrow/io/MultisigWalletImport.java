package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.InputStream;

public interface MultisigWalletImport extends Import {
    String getWalletImportDescription();
    Wallet importWallet(InputStream inputStream) throws ImportException;
}
