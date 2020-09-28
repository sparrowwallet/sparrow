package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.Network;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.InputStream;

public interface WalletImport extends Import, FileImport {
    String getWalletImportDescription();
    Wallet importWallet(Network network, InputStream inputStream, String password) throws ImportException;
}
