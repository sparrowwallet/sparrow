package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.nio.file.Files;

public class Sparrow implements WalletExport {
    private static final Logger log = LoggerFactory.getLogger(Sparrow.class);

    @Override
    public String getName() {
        return "Sparrow";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SPARROW;
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream) throws ExportException {
        try {
            Storage storage = AppServices.get().getOpenWallets().get(wallet);
            Files.copy(storage.getWalletFile().toPath(), outputStream);
            outputStream.flush();
        } catch(Exception e) {
            log.error("Error exporting Sparrow wallet file", e);
            throw new ExportException("Error exporting Sparrow wallet file", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Exports a copy of your Sparrow wallet file, which can be loaded in another Sparrow instance running on any supported platform.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        Storage storage = AppServices.get().getOpenWallets().get(wallet);
        if(storage != null && (storage.getEncryptionPubKey() == null || Storage.NO_PASSWORD_KEY.equals(storage.getEncryptionPubKey()))) {
            return "json";
        }

        return "";
    }

    @Override
    public boolean isWalletExportScannable() {
        return false;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }
}
