package com.sparrowwallet.sparrow.io;

import com.google.common.io.Files;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;

public class Sparrow implements WalletImport, WalletExport {
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
            File tempFile = File.createTempFile(wallet.getName(), null);
            Storage tempStorage = new Storage(PersistenceType.JSON, tempFile);
            tempStorage.setKeyDeriver(storage.getKeyDeriver());
            tempStorage.setEncryptionPubKey(storage.getEncryptionPubKey());
            tempStorage.saveWallet(wallet);
            Files.copy(tempStorage.getWalletFile(), outputStream);
            outputStream.flush();
            tempStorage.getWalletFile().delete();
        } catch(Exception e) {
            log.error("Error exporting Sparrow wallet file", e);
            throw new ExportException("Error exporting Sparrow wallet file", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Exports your Sparrow wallet file, which can be imported into another Sparrow instance running on any supported platform.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        try {
            Storage storage = AppServices.get().getOpenWallets().get(wallet);
            return storage.isEncrypted() ? "" : PersistenceType.JSON.getExtension();
        } catch(IOException e) {
            //ignore
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

    @Override
    public boolean isEncrypted(File file) {
        return Storage.isEncrypted(file);
    }

    @Override
    public String getWalletImportDescription() {
        return "Imports an exported Sparrow wallet file into Sparrow's wallets folder.";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("sparrow", null);
            java.nio.file.Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Storage storage = new Storage(PersistenceType.JSON, tempFile);
            if(!isEncrypted(tempFile)) {
                return storage.loadUnencryptedWallet().getWallet();
            } else {
                WalletBackupAndKey walletBackupAndKey = storage.loadEncryptedWallet(password);
                walletBackupAndKey.getWallet().decrypt(walletBackupAndKey.getKey());
                return walletBackupAndKey.getWallet();
            }
        } catch(IOException | StorageException e) {
            log.error("Error importing Sparrow wallet", e);
            throw new ImportException("Error importing Sparrow wallet", e);
        } finally {
            if(tempFile != null) {
                tempFile.delete();
            }
        }
    }

    @Override
    public boolean isWalletImportScannable() {
        return false;
    }
}
