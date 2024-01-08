package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.OutputStream;

public interface WalletExport extends ImportExport {
    void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException;
    String getWalletExportDescription();
    String getExportFileExtension(Wallet wallet);
    boolean isWalletExportScannable();
    boolean walletExportRequiresDecryption();
    default boolean isWalletExportFile() {
        return true;
    }
    default boolean exportsAllWallets() {
        return false;
    }
}
