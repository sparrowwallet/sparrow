package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.Pbkdf2KeyDeriver;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.*;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.List;

public class Bip129 implements KeystoreFileExport, KeystoreFileImport, WalletExport, WalletImport {
    @Override
    public String getName() {
        return "BSMS";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.BSMS;
    }

    @Override
    public String getKeystoreExportDescription() {
        return "Exports the keystore in the Bitcoin Secure Multisig Setup (BSMS) format.";
    }

    @Override
    public void exportKeystore(Keystore keystore, OutputStream outputStream) throws ExportException {
        if(!keystore.isValid()) {
            throw new ExportException("Invalid keystore");
        }

        try {
            String record = "BSMS 1.0\n00\n[" +
                    keystore.getKeyDerivation().toString() +
                    "]" +
                    keystore.getExtendedPublicKey().toString() +
                    "\n" +
                    keystore.getLabel();
            outputStream.write(record.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) {
            throw new ExportException("Error writing BSMS file", e);
        }
    }

    @Override
    public boolean requiresSignature() {
        //Due to poor vendor support of multiline message signing at the xpub derivation path, signing BSMS keystore exports is configurable (default false)
        return Config.get().isSignBsmsExports();
    }

    @Override
    public void addSignature(Keystore keystore, String signature, OutputStream outputStream) throws ExportException {
        try {
            String append = "\n" + signature;
            outputStream.write(append.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) {
            throw new ExportException("Error writing BSMS file", e);
        }
    }

    @Override
    public String getExportFileExtension(Keystore keystore) {
        return "bsms";
    }

    @Override
    public boolean isKeystoreExportScannable() {
        return true;
    }

    @Override
    public boolean isEncrypted(File file) {
        try {
            try(BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                String text = CharStreams.toString(reader);
                return Utils.isHex(text.trim());
            }
        } catch(Exception e) {
            return false;
        }
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            if(password != null) {
                reader = decryptImport(password, reader);
            }

            String header = reader.readLine();
            String token = reader.readLine();
            String descriptor = reader.readLine();
            String label = reader.readLine();
            String signature = reader.readLine();

            return getKeystore(header, token, descriptor, label, signature);
        } catch(MnemonicException.MnemonicWordException e) {
            throw new ImportException("Error importing BSMS: Invalid mnemonic word " + e.badWord, e);
        } catch(MnemonicException.MnemonicChecksumException e) {
            throw new ImportException("Error importing BSMS: Invalid mnemonic checksum", e);
        } catch(Exception e) {
            throw new ImportException("Error importing BSMS", e);
        }
    }

    private BufferedReader decryptImport(String password, BufferedReader streamReader) throws Exception {
        byte[] token;
        if((password.length() == 16 || password.length() == 32) && Utils.isHex(password)) {
            token = Utils.hexToBytes(password);
        } else if(Utils.isNumber(password)) {
            BigInteger bi = new BigInteger(password);
            token = Utils.bigIntegerToBytes(bi, bi.toByteArray().length >= 16 ? 16 : 8);
        } else if(password.split(" ").length == 6 || password.split(" ").length == 12) {
            List<String> mnemonicWords = Arrays.asList(password.split(" "));
            token = Bip39MnemonicCode.INSTANCE.toEntropy(mnemonicWords);
        } else {
            throw new ImportException("Provided password needs to be in hexadecimal, decimal or mnemonic format.");
        }

        String hex = CharStreams.toString(streamReader).trim();
        byte[] data = Utils.hexToBytes(hex);
        byte[] mac = Arrays.copyOfRange(data, 0, 32);
        byte[] iv = Arrays.copyOfRange(mac, 0, 16);
        byte[] ciphertext = Arrays.copyOfRange(data, 32, data.length);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

        Pbkdf2KeyDeriver pbkdf2KeyDeriver = new Pbkdf2KeyDeriver(token, 2048, 256);
        byte[] key = pbkdf2KeyDeriver.deriveKey("No SPOF").getKeyBytes();

        Key keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);
        String plaintextString = new String(plaintext, StandardCharsets.UTF_8);

        SecretKeySpec secretKeySpec = new SecretKeySpec(Sha256Hash.hash(key), "HmacSHA256");
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(secretKeySpec);
        String macData = Utils.bytesToHex(token) + plaintextString;
        byte[] calculatedMac = hmac.doFinal(macData.getBytes(StandardCharsets.UTF_8));
        if(!Arrays.equals(mac, calculatedMac)) {
            throw new ImportException("Message digest authentication failed.");
        }

        return new BufferedReader(new StringReader(plaintextString));
    }

    private Keystore getKeystore(String header, String token, String descriptor, String label, String signature) throws ImportException {
        OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor("sh(" + descriptor + ")");
        Wallet wallet = outputDescriptor.toWallet();
        Keystore keystore = wallet.getKeystores().get(0);
        keystore.setLabel(label);

        if(signature != null) {
            try {
                String message = header + "\n" + token + "\n" + descriptor + "\n" + label;
                keystore.getExtendedPublicKey().getKey().verifyMessage(message, signature);
            } catch(SignatureException e) {
                throw new ImportException("Signature did not match provided public key", e);
            }
        }

        return keystore;
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Imports a keystore that was exported using the Bitcoin Secure Multisig Setup (BSMS) format.";
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return true;
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        try {
            String record = "BSMS 1.0\n" +
                    OutputDescriptor.getOutputDescriptor(wallet) +
                    "\n/0/*,/1/*\n" +
                    wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next().getAddress();
            outputStream.write(record.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) {
            throw new ExportException("Error exporting BSMS format", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Exports a multisig wallet in the Bitcoin Secure Multisig Setup (BSMS) format for import by other signers in the quorum.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "bsms";
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
    public String getWalletImportDescription() {
        return "Imports a multisig wallet in the Bitcoin Secure Multisig Setup (BSMS) format that has been created by another signer in the quorum.";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            if(password != null) {
                reader = decryptImport(password, reader);
            }

            String header = reader.readLine();
            String descriptor = reader.readLine();
            String paths = reader.readLine();
            String address = reader.readLine();

            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(descriptor);
            return outputDescriptor.toWallet();
        } catch(Exception e) {
            throw new ImportException("Error importing BSMS format", e);
        }
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }
}
