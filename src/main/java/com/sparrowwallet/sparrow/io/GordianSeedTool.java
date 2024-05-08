package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.InputStream;

public class GordianSeedTool implements KeystoreFileImport {
    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public String getName() {
        return "Gordian Seed Tool";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.GORDIAN_SEED_TOOL;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        throw new ImportException("Only QR imports are supported.");
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Select your seed and scan the QR code created by Authenticate > Derive Key > Other Key Derivations > " + Network.getCanonical().getCapitalizedName() + " > Master Key > Account Descriptor. Click the share icon at the bottom.";
    }

    @Override
    public boolean isFileFormatAvailable() {
        return false;
    }
}
