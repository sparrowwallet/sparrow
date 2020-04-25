package com.sparrowwallet.sparrow.external;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.OutputStream;

public interface WalletExport extends Export {
    void exportWallet(Wallet wallet, OutputStream outputStream) throws ExportException;
    String getWalletExportDescription();
}
