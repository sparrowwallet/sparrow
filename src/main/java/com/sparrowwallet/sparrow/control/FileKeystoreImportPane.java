package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreFileImport;

import java.io.*;

public class FileKeystoreImportPane extends FileImportPane {
    protected final Wallet wallet;
    private final KeystoreFileImport importer;
    private final KeyDerivation requiredDerivation;

    public FileKeystoreImportPane(Wallet wallet, KeystoreFileImport importer, KeyDerivation requiredDerivation) {
        super(importer, importer.getName(), "Keystore import", importer.getKeystoreImportDescription(getAccount(wallet, requiredDerivation)), "image/" + importer.getWalletModel().getType() + ".png", importer.isKeystoreImportScannable(), importer.isFileFormatAvailable());
        this.wallet = wallet;
        this.importer = importer;
        this.requiredDerivation = requiredDerivation;
    }

    protected void importFile(String fileName, InputStream inputStream, String password) throws ImportException {
        Keystore keystore = getScannedKeystore(wallet.getScriptType());
        if(keystore == null) {
            keystore = importer.getKeystore(wallet.getScriptType(), inputStream, password);
        }

        if(requiredDerivation != null && !requiredDerivation.getDerivation().equals(keystore.getKeyDerivation().getDerivation())) {
            setError("Incorrect derivation", "This account requires a derivation of " + requiredDerivation.getDerivationPath() + ", but the imported keystore has a derivation of " + keystore.getKeyDerivation().getDerivationPath() + ".");
        } else {
            EventManager.get().post(new KeystoreImportEvent(keystore));
        }
    }
}
