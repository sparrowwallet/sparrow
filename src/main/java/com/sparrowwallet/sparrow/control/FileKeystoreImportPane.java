package com.sparrowwallet.sparrow.control;

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

    public FileKeystoreImportPane(Wallet wallet, KeystoreFileImport importer) {
        super(importer, importer.getName(), "Keystore file import", importer.getKeystoreImportDescription(), "image/" + importer.getWalletModel().getType() + ".png");
        this.wallet = wallet;
        this.importer = importer;
    }

    protected void importFile(String fileName, InputStream inputStream, String password) throws ImportException {
        Keystore keystore = importer.getKeystore(wallet.getScriptType(), inputStream, password);
        EventManager.get().post(new KeystoreImportEvent(keystore));
    }
}
