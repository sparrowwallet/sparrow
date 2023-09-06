package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletImportEvent;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.WalletImport;

import java.io.InputStream;

public class FileWalletImportPane extends FileImportPane {
    private final WalletImport importer;

    public FileWalletImportPane(WalletImport importer) {
        super(importer, importer.getName(), "Wallet import", importer.getWalletImportDescription(), "image/" + importer.getWalletModel().getType() + ".png", importer.isWalletImportScannable(), importer.isWalletImportFileFormatAvailable());
        this.importer = importer;
    }

    @Override
    protected void importFile(String fileName, InputStream inputStream, String password) throws ImportException {
        Wallet wallet;
        if(getScannedWallets() != null && !getScannedWallets().isEmpty()) {
            wallet = getScannedWallets().iterator().next();
        } else {
            wallet = importer.importWallet(inputStream, password);
        }

        if(wallet.getName() == null) {
            wallet.setName(fileName);
        }
        EventManager.get().post(new WalletImportEvent(wallet));
    }
}
