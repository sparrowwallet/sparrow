package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.wallet.Keystore;

import java.io.OutputStream;

public interface KeystoreFileExport extends KeystoreExport {
    void exportKeystore(Keystore keystore, OutputStream outputStream) throws ExportException;
    boolean requiresSignature();
    void addSignature(Keystore keystore, String signature, OutputStream outputStream) throws ExportException;
    String getExportFileExtension(Keystore keystore);
    boolean isKeystoreExportScannable();
    default boolean isKeystoreExportFile() {
        return true;
    }
}
