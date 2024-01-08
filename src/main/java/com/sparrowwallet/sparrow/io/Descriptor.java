package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
            bufferedWriter.write("# Receive and change descriptor (BIP389):");
            bufferedWriter.newLine();

            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.DEFAULT_PURPOSES, null);
            bufferedWriter.write(outputDescriptor.toString(true));
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.newLine();

            bufferedWriter.write("# Receive descriptor (Bitcoin Core):");
            bufferedWriter.newLine();
            OutputDescriptor receiveDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.RECEIVE, null);
            bufferedWriter.write(receiveDescriptor.toString(true));
            bufferedWriter.newLine();
            bufferedWriter.newLine();
            bufferedWriter.write("# Change descriptor (Bitcoin Core):");
            bufferedWriter.newLine();
            OutputDescriptor changeDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.CHANGE, null);
            bufferedWriter.write(changeDescriptor.toString(true));
            bufferedWriter.newLine();

            bufferedWriter.flush();
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
        return true;
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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            inputStream.transferTo(baos);
            InputStream firstClone = new ByteArrayInputStream(baos.toByteArray());
            InputStream secondClone = new ByteArrayInputStream(baos.toByteArray());

            try {
                return PdfUtils.getOutputDescriptor(firstClone).toWallet();
            } catch(Exception e) {
                //ignore
            }

            List<String> paragraphs = getParagraphs(secondClone);
            for(String paragraph : paragraphs) {
                OutputDescriptor descriptor = OutputDescriptor.getOutputDescriptor(paragraph);
                return descriptor.toWallet();
            }

            throw new ImportException("Could not find an output descriptor in the file");
        } catch(Exception e) {
            throw new ImportException("Error importing output descriptor", e);
        }
    }

    private static List<String> getParagraphs(InputStream inputStream) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        for(String line : reader.lines().map(String::trim).toArray(String[]::new)) {
            if(line.isEmpty()) {
                if(paragraph.length() > 0) {
                    paragraphs.add(paragraph.toString());
                    paragraph.setLength(0);
                }
            } else if(line.startsWith("#")) {
                continue;
            } else {
                paragraph.append(line);
            }
        }

        if(paragraph.length() > 0) {
            paragraphs.add(paragraph.toString());
        }

        return paragraphs;
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }
}
