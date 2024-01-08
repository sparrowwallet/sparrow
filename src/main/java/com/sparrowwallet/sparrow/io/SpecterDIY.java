package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class SpecterDIY implements KeystoreFileImport, WalletExport {
    private static final Logger log = LoggerFactory.getLogger(SpecterDIY.class);

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            String text = CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String outputDesc = "sh(" + text + ")";
            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(outputDesc);
            Wallet wallet = outputDescriptor.toWallet();

            if(wallet.getKeystores().size() != 1) {
                throw new ImportException("Could not determine keystore from import");
            }

            Keystore keystore = wallet.getKeystores().get(0);
            keystore.setLabel(getName());
            keystore.setWalletModel(getWalletModel());
            keystore.setSource(KeystoreSource.HW_AIRGAPPED);

            return keystore;
        } catch(IOException e) {
            throw new ImportException("Error getting " + getName() + " keystore", e);
        }
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public String getKeystoreImportDescription(int account) {
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

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.append("addwallet ").append(wallet.getFullName()).append("&").append(OutputDescriptor.getOutputDescriptor(wallet).toString().replace('\'', 'h')).append("\n");
            writer.flush();
        } catch(Exception e) {
            log.error("Error exporting " + getName() + " wallet", e);
            throw new ExportException("Error exporting " + getName() + " wallet", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Export file or QR that can be read by your Specter DIY using the Wallets > Add Wallet feature.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "txt";
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }
}
