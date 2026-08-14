package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.IOUtils;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

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
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        File tempDir = null;
        try {
            Wallet exportedWallet = !wallet.isMasterWallet() ? wallet.getMasterWallet() : wallet;
            PersistenceType persistenceType = PersistenceType.DB;
            Persistence persistence = persistenceType.getInstance();
            Storage storage = AppServices.get().getOpenWallets().get(exportedWallet);
            tempDir = Files.createTempDirectory("sparrow").toFile();
            File tempFile = new File(tempDir, exportedWallet.getName() + "." + persistenceType.getExtension());
            Storage tempStorage = new Storage(persistence, tempFile);
            tempStorage.setKeyDeriver(storage.getKeyDeriver());
            tempStorage.setEncryptionPubKey(storage.getEncryptionPubKey());

            Wallet copy = exportedWallet.copy();
            tempStorage.saveWallet(copy);
            for(Wallet childWallet : copy.getChildWallets()) {
                tempStorage.saveWallet(childWallet);
            }
            persistence.close();
            Files.copy(tempStorage.getWalletFile().toPath(), outputStream);
            outputStream.flush();
        } catch(Exception e) {
            log.error("Error exporting Sparrow wallet file", e);
            throw new ExportException("Error exporting Sparrow wallet file", e);
        } finally {
            deleteTempDirectory(tempDir);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Exports your Sparrow wallet file, which can be imported into another Sparrow instance running on any supported platform. If the wallet is encrypted, the same password is used to encrypt the exported file.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return PersistenceType.DB.getExtension();
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
        Storage storage = null;
        File tempDir = null;
        try {
            tempDir = Files.createTempDirectory("sparrow").toFile();
            File tempFile = new File(tempDir, "sparrow");
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            PersistenceType persistenceType = Storage.detectPersistenceType(tempFile);
            persistenceType = (persistenceType == null ? PersistenceType.JSON : persistenceType);
            if(persistenceType != PersistenceType.JSON || !isEncrypted(tempFile)) {
                File tempTypedFile = new File(tempFile.getParentFile(), tempFile.getName() + "." + persistenceType.getExtension());
                tempFile.renameTo(tempTypedFile);
                tempFile = tempTypedFile;
            }

            storage = new Storage(persistenceType, tempFile);
            Wallet wallet;
            if(!isEncrypted(tempFile)) {
                wallet = storage.loadUnencryptedWallet().getWallet();
            } else {
                WalletAndKey walletAndKey = storage.loadEncryptedWallet(password);
                wallet = walletAndKey.getWallet();
                wallet.decrypt(walletAndKey.getKey());
                for(Map.Entry<WalletAndKey, Storage> entry : walletAndKey.getChildWallets().entrySet()) {
                    entry.getKey().getWallet().decrypt(entry.getKey().getKey());
                }
            }

            return wallet;
        } catch(IOException | StorageException e) {
            throw new ImportException("Error importing Sparrow wallet", e);
        } finally {
            if(storage != null) {
                storage.closeAndWait();
            }

            deleteTempDirectory(tempDir);
        }
    }

    private void deleteTempDirectory(File tempDir) {
        if(tempDir != null) {
            File[] tempFiles = tempDir.listFiles();
            if(tempFiles != null) {
                for(File file : tempFiles) {
                    IOUtils.secureDelete(file);
                }
            }

            tempDir.delete();
        }
    }

    @Override
    public boolean isWalletImportScannable() {
        return false;
    }

    @Override
    public boolean exportsAllWallets() {
        return true;
    }
}
