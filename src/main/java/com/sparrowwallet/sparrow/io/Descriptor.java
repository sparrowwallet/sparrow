package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Descriptor implements WalletImport, WalletExport {

    @Override
    public String getName() {
        return "Output Descriptor";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.BITCOIN_CORE;
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream) throws ExportException {
        try {
            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.DEFAULT_PURPOSES, null);
            String outputDescriptorString = outputDescriptor.toString(true);
            outputStream.write(outputDescriptorString.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch(Exception e) {
            throw new ExportException("Error writing output descriptor", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "The output descriptor is a standardized description of the wallet compatible with Bitcoin Core, and can be used to create a watch-only copy using the Edit button on the Settings tab of a new Sparrow wallet.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "txt";
    }

    @Override
    public boolean isWalletExportScannable() {
        return false;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public String getWalletImportDescription() {
        return "Import a file containing the output descriptor of a wallet. The output descriptor is a standardized description of the wallet compatible with Bitcoin Core.";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        try {
            String outputDescriptor = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            OutputDescriptor descriptor = OutputDescriptor.getOutputDescriptor(outputDescriptor.trim());
            return descriptor.toWallet();
        } catch(Exception e) {
            throw new ImportException("Error importing output descriptor", e);
        }
    }

    @Override
    public boolean isWalletImportScannable() {
        return false;
    }
}
