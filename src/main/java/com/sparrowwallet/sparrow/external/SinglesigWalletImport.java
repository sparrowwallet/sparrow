package com.sparrowwallet.sparrow.external;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.InputStream;

public interface SinglesigWalletImport extends Import {
    String getWalletImportDescription();
    Wallet importWallet(InputStream inputStream, ScriptType scriptType) throws ImportException;
}
