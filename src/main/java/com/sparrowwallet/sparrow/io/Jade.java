package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.InputStream;

public class Jade implements KeystoreFileImport {
    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public String getName() {
        return "Jade";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.JADE;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        throw new ImportException("Failed to detect a valid " + scriptType.getDescription() + " keystore.");
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import QR created on your Jade by selecting Xpub Export from the Settings menu once you have loaded your seed.";
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public boolean isFileFormatAvailable() {
        return false;
    }
}
