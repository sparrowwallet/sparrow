package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.Pbkdf2KeyDeriver;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Bip129 implements KeystoreFileExport, KeystoreFileImport, WalletExport, WalletImport {
    private static final Logger log = LoggerFactory.getLogger(Bip129.class);

    private static final String NO_PATH_RESTRICTIONS = "No path restrictions";

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
    public Keystore getKeystore(PolicyType policyType, ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
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
        } else {
            log.info("BSMS record for keystore " + label + " is not signed, the provided public key cannot be verified as originating from the signer");
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
                    OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.DEFAULT_PURPOSES, null) +
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
            if(header == null || !header.startsWith("BSMS")) {
                throw new ImportException("Not a BSMS file");
            }

            String descriptor = reader.readLine();
            String paths = reader.readLine();
            String address = reader.readLine();

            OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(descriptor);
            Wallet wallet = outputDescriptor.toWallet();

            try {
                wallet.checkWallet();
            } catch(InvalidWalletException e) {
                throw new IllegalStateException("This file does not describe a valid wallet: " + e.getMessage());
            }

            List<KeyPurpose> keyPurposes = getPathKeyPurposes(paths);
            checkFirstAddress(wallet, outputDescriptor, descriptor, keyPurposes, address);

            return wallet;
        } catch(Exception e) {
            throw new ImportException("Error importing BSMS format", e);
        }
    }

    //Returns the key purposes of the provided path restrictions in the order given, or an empty list if the record does not restrict derivation paths
    //Sparrow derives the standard receive and change chains only, so a record restricted to any other derivation cannot be honoured whether or not it supplies a first address
    private List<KeyPurpose> getPathKeyPurposes(String paths) {
        if(paths == null || paths.isBlank() || paths.trim().equalsIgnoreCase(NO_PATH_RESTRICTIONS)) {
            return Collections.emptyList();
        }

        List<KeyPurpose> keyPurposes = new ArrayList<>();
        for(String path : paths.split(",")) {
            KeyPurpose keyPurpose = getPathKeyPurpose(path.trim());
            if(keyPurpose == null) {
                throw new IllegalStateException("This file restricts derivation to " + paths.trim() + ", which is not the standard receive and change derivation. " +
                        "Addresses derived from it would not match those of the other signers in the quorum.");
            }

            keyPurposes.add(keyPurpose);
        }

        return keyPurposes;
    }

    private KeyPurpose getPathKeyPurpose(String path) {
        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
            if(path.equals("/" + keyPurpose.getPathIndex().num() + "/*")) {
                return keyPurpose;
            }
        }

        return null;
    }

    private void checkFirstAddress(Wallet wallet, OutputDescriptor outputDescriptor, String descriptor, List<KeyPurpose> keyPurposes, String address) {
        //BIP129 requires the first address, and it is the only means of detecting a coordinator serving a different set of keys to each signer
        if(address == null || address.isBlank()) {
            throw new IllegalStateException("This file does not provide a first address, so the descriptor cannot be verified against the other signers in the quorum.");
        }

        Address recordAddress;
        try {
            recordAddress = Address.fromString(address.trim());
        } catch(InvalidAddressException e) {
            throw new IllegalStateException("The first address in this file (" + address.trim() + ") is not a valid address: " + e.getMessage());
        }

        Address firstAddress = getFirstAddress(wallet, outputDescriptor, keyPurposes);
        if(firstAddress.equals(recordAddress)) {
            return;
        }

        if(OutputDescriptor.LEGACY_MULTI_PATTERN.matcher(descriptor).find()) {
            throw new IllegalStateException("The first address in this BSMS record (" + recordAddress + ") does not match the first address of " + firstAddress + " derived by sorting the provided keys");
        } else {
            throw new IllegalStateException("The first address in this file (" + recordAddress + ") does not match the first address of the provided descriptor (" + firstAddress + "). " +
                    "The coordinator may be providing a different set of keys to each signer in the quorum.");
        }
    }

    //BIP129 defines the first address as the first address of the first path restriction, or where derivation is not restricted, the descriptor's only address
    private Address getFirstAddress(Wallet wallet, OutputDescriptor outputDescriptor, List<KeyPurpose> keyPurposes) {
        if(!keyPurposes.isEmpty()) {
            return wallet.getNode(keyPurposes.getFirst()).getChildren().iterator().next().getAddress();
        }

        //Only the receive and change chains are derived here whatever chain the descriptor names, so a descriptor of multiple addresses starts at the first receive address
        if(outputDescriptor.describesMultipleAddresses()) {
            return wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next().getAddress();
        }

        //A record without path restrictions provides a descriptor fixed at one address, which is only present in this wallet if it is at an index on the receive or change chain
        List<ChildNumber> childDerivation = outputDescriptor.getChildDerivation();
        List<ChildNumber> fixedDerivation = childDerivation.subList(1, childDerivation.size());
        if(fixedDerivation.size() != 2 || KeyPurpose.fromChildNumber(fixedDerivation.getFirst()) == null) {
            throw new IllegalStateException("This file restricts derivation to " + KeyDerivation.writePath(fixedDerivation) + ", which is not the standard receive and change derivation. " +
                    "Addresses derived from it would not match those of the other signers in the quorum.");
        }

        return outputDescriptor.getAddress(childDerivation);
    }

    @Override
    public boolean isWalletImportScannable() {
        return true;
    }
}
