package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SpecterDIY implements KeystoreFileImport {
    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            String text = CharStreams.toString(new InputStreamReader(inputStream));
            String outputDesc = "sh(" + text + ")";
            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(outputDesc);
            Wallet wallet = outputDescriptor.toWallet();

            if(wallet.getKeystores().size() != 1) {
                throw new ImportException("Could not determine keystore from import");
            }

            Keystore keystore = wallet.getKeystores().get(0);
            keystore.setLabel(getName());
            keystore.setWalletModel(WalletModel.SPECTER_DIY);
            keystore.setSource(KeystoreSource.HW_AIRGAPPED);

            return keystore;
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Import file or QR created by using the Master Public Keys feature on your Specter DIY device. Note the default is P2WPKH for Single Signature, and P2WSH for Multi Signature.";
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public String getName() {
        return "Specter DIY";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SPECTER_DIY;
    }
}
