package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Specter implements WalletImport, WalletExport {
    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream) throws ExportException {
        try {
            SpecterWallet specterWallet = new SpecterWallet();
            specterWallet.label = wallet.getName();
            specterWallet.blockheight = wallet.getStoredBlockHeight();
            specterWallet.descriptor = OutputDescriptor.getOutputDescriptor(wallet).toString(true);

            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            String json = gson.toJson(specterWallet);
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
        } catch(Exception e) {
            throw new ExportException(e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Export a Specter wallet that can be read by Specter Desktop using Add new wallet > Import from wallet software";
    }

    @Override
    public String getExportFileExtension() {
        return "json";
    }

    @Override
    public String getWalletImportDescription() {
        return "Import a Specter wallet created by using the Wallets > Settings > Export to Wallet Software in Specter Desktop";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        try {
            Gson gson = new Gson();
            SpecterWallet specterWallet = gson.fromJson(new InputStreamReader(inputStream), SpecterWallet.class);

            if(specterWallet.descriptor != null) {
                OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(specterWallet.descriptor);
                Wallet wallet = outputDescriptor.toWallet();
                wallet.setName(specterWallet.label);

                if(!wallet.isValid()) {
                    throw new ImportException("Specter wallet file did not contain a valid wallet");
                }

                return wallet;
            }
        } catch(Exception e) {
            throw new ImportException(e);
        }

        throw new ImportException("File was not a valid Specter wallet");
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public boolean isScannable() {
        return true;
    }

    @Override
    public String getName() {
        return "Specter";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SPECTER;
    }

    public static class SpecterWallet {
        public String label;
        public Integer blockheight;
        public String descriptor;
    }
}
