package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
    public Keystore getKeystore(PolicyType policyType, ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            String text = CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).trim();
            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(text);
            if(policyType == PolicyType.SINGLE_SP) {
                throw new IllegalArgumentException("Export does not contain the spscan value for silent payments");
            } else if(outputDescriptor.getScriptType() != scriptType) {
                throw new IllegalArgumentException("The exported xpub is for " + outputDescriptor.getScriptType().getDescription() + ", not " + scriptType.getDescription()
                        + ". Select " + scriptType.getDescription() + " in the options when using Export Xpub on your Jade, and export again.");
            }

            Wallet wallet = outputDescriptor.toWallet();
            if(wallet.getKeystores().size() != 1) {
                throw new IllegalArgumentException("Could not determine keystore from import");
            }

            Keystore keystore = wallet.getKeystores().getFirst();
            keystore.setLabel(getName());
            keystore.setWalletModel(getWalletModel());
            keystore.setSource(KeystoreSource.HW_AIRGAPPED);

            return keystore;
        } catch(Exception e) {
            throw new ImportException("Error getting " + getName() + " keystore", e);
        }
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import QR created on your Jade by selecting Options > Wallet > Export Xpub once you have loaded your seed, or the file written by Options > USB Storage > Export Xpub. Make sure to select Singlesig as the Wallet type in the Options menu there.";
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public boolean isFileFormatAvailable() {
        return true;
    }
}
