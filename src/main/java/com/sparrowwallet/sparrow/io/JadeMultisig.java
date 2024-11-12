package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.InputStream;

public class JadeMultisig extends ColdcardMultisig {
    @Override
    public String getName() {
        return "Jade Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.JADE;
    }

    @Override
    public String getWalletExportDescription() {
        return "Export a QR that allows Jade to import a multisig wallet using the Scan feature.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return null;
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }

    @Override
    public boolean isWalletExportFile() {
        return false;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }

    @Override
    public String getWalletImportDescription() {
        return "Import the QR created using Options > Wallet > Registered Wallets on your Jade.";
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }

    @Override
    public boolean isWalletImportFileFormatAvailable() {
        return false;
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        Wallet wallet = super.importWallet(inputStream, password);
        for(Keystore keystore : wallet.getKeystores()) {
            keystore.setLabel(keystore.getLabel().replace("Coldcard", "Jade"));
            keystore.setWalletModel(WalletModel.JADE);
        }

        return wallet;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        throw new ImportException("Failed to detect a valid " + scriptType.getDescription() + " keystore.");
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import QR created on your Jade by selecting Options > Wallet > Export Xpub once you have loaded your seed. Make sure to select Multisig as the Wallet type in the Options menu there.";
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
