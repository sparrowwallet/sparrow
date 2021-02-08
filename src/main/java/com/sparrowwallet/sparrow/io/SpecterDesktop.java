package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.InvalidWalletException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SpecterDesktop implements WalletImport, WalletExport {
    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream) throws ExportException {
        try {
            SpecterWallet specterWallet = new SpecterWallet();
            specterWallet.label = wallet.getName();
            specterWallet.blockheight = wallet.getTransactions().values().stream().mapToInt(BlockTransactionHash::getHeight).min().orElse(wallet.getStoredBlockHeight());
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
            SpecterWallet specterWallet = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), SpecterWallet.class);

            if(specterWallet.descriptor != null) {
                OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(specterWallet.descriptor);
                Wallet wallet = outputDescriptor.toWallet();
                wallet.setName(specterWallet.label);

                try {
                    wallet.checkWallet();
                } catch(InvalidWalletException e) {
                    throw new ImportException("Imported Specter wallet was invalid: " + e.getMessage());
                }

                return wallet;
            }
        } catch(Exception e) {
            throw new ImportException(e);
        }

        throw new ImportException("File was not a valid Specter Desktop wallet");
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }

    @Override
    public boolean isWalletExportScannable() {
        return true;
    }

    @Override
    public String getName() {
        return "Specter Desktop";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SPECTER_DESKTOP;
    }

    public static class SpecterWallet {
        public String label;
        public Integer blockheight;
        public String descriptor;
    }
}
