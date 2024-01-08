package com.sparrowwallet.sparrow.io;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Electrum implements KeystoreFileImport, WalletImport, WalletExport {
    private static final Logger log = LoggerFactory.getLogger(Electrum.class);

    @Override
    public String getName() {
        return "Electrum";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.ELECTRUM;
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import a single keystore from an Electrum wallet (use File > Import > Electrum to import a multisig wallet).";
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        Wallet wallet = importWallet(inputStream, password);

        if(!wallet.getPolicyType().equals(PolicyType.SINGLE) || wallet.getKeystores().size() != 1) {
            throw new ImportException("Multisig wallet detected - import it using File > Import Wallet");
        }

        if(!wallet.getScriptType().equals(scriptType)) {
            //TODO: Derive appropriate ScriptType keystore from xprv if present
            throw new ImportException("Wallet has an incompatible script type of " + wallet.getScriptType() + ", and the correct script type cannot be derived without the master private key.");
        }

        return wallet.getKeystores().get(0);
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        Reader reader;
        if(password != null) {
            ECKey decryptionKey = Pbkdf2KeyDeriver.DEFAULT_INSTANCE.deriveECKey(password);
            reader = new InputStreamReader(new InflaterInputStream(new ECIESInputStream(inputStream, decryptionKey)), StandardCharsets.UTF_8);
        } else {
            reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        }

        try {
            Gson gson = new Gson();
            Type stringStringMap = new TypeToken<Map<String, JsonElement>>(){}.getType();
            Map<String,JsonElement> map = gson.fromJson(reader, stringStringMap);

            ElectrumJsonWallet ew = new ElectrumJsonWallet();
            if(map.get("wallet_type") == null) {
                throw new ImportException("File was not a valid Electrum wallet");
            }

            ew.wallet_type = map.get("wallet_type").getAsString();

            for(String key : map.keySet()) {
                if(key.startsWith("x") || key.equals("keystore")) {
                    ElectrumKeystore ek = gson.fromJson(map.get(key), ElectrumKeystore.class);
                    if(ek.root_fingerprint == null && ek.ckcc_xfp != null) {
                        byte[] le = new byte[4];
                        Utils.uint32ToByteArrayLE(Long.parseLong(ek.ckcc_xfp), le, 0);
                        ek.root_fingerprint = Utils.bytesToHex(le).toUpperCase(Locale.ROOT);
                    }
                    ew.keystores.put(key, ek);
                }

                if(key.equals("labels")) {
                    JsonObject jsonObject = (JsonObject)map.get(key);
                    for(String labelKey : jsonObject.keySet()) {
                        ew.labels.put(labelKey, jsonObject.get(labelKey).getAsString());
                    }
                }

                if(key.equals("gap_limit") && map.get(key) instanceof JsonPrimitive gapLimit) {
                    ew.gap_limit = gapLimit.getAsInt();
                }

                if(key.equals("addresses")) {
                    ew.addresses = gson.fromJson(map.get(key), ElectrumAddresses.class);
                }

                if(key.equals("verified_tx3")) {
                    JsonObject jsonObject = (JsonObject)map.get(key);
                    for(String txKey : jsonObject.keySet()) {
                        Sha256Hash txHash = Sha256Hash.wrap(txKey);
                        JsonArray array = jsonObject.getAsJsonArray(txKey);
                        if(array != null && array.size() > 3) {
                            int height = array.get(0).getAsInt();
                            Date date = new Date(array.get(1).getAsLong() * 1000);
                            long fee = array.get(2).getAsLong();
                            Sha256Hash blockHash = Sha256Hash.wrap(array.get(3).getAsString());

                            JsonObject transactions = (JsonObject)map.get("transactions");
                            if(transactions != null) {
                                String txhex = transactions.get(txKey).getAsString();
                                if(txhex != null) {
                                    Transaction transaction = new Transaction(Utils.hexToBytes(txhex));

                                    BlockTransaction blockTransaction = new BlockTransaction(txHash, height, date, fee, transaction, blockHash);
                                    ew.transactions.put(txHash, blockTransaction);
                                }
                            }
                        }
                    }
                }
            }

            Wallet wallet = new Wallet();
            ScriptType scriptType = null;

            for(ElectrumKeystore ek : ew.keystores.values()) {
                Keystore keystore = new Keystore();
                ExtendedKey xPub = ExtendedKey.fromDescriptor(ek.xpub);
                String derivationPath = ek.derivation;
                if(derivationPath == null) {
                    derivationPath = "m/0";
                }
                String masterFingerprint = ek.root_fingerprint;
                if(masterFingerprint == null) {
                    masterFingerprint = Utils.bytesToHex(xPub.getParentFingerprint());
                }

                if("hardware".equals(ek.type)) {
                    keystore.setSource(KeystoreSource.HW_USB);
                    keystore.setWalletModel(WalletModel.fromType(ek.hw_type));
                    if(keystore.getWalletModel() == null) {
                        throw new ImportException("Wallet has keystore of unknown hardware wallet type \"" + ek.hw_type + "\".");
                    }
                    if(keystore.getWalletModel().equals(WalletModel.TREZOR_1)) {
                        keystore.setWalletModel(WalletModel.TREZOR_T);
                    }
                } else if("bip32".equals(ek.type)) {
                    if(ek.xprv != null && ek.seed == null) {
                        throw new ImportException("Electrum does not support exporting BIP39 derived seeds, as it does not store the mnemonic words. Only seeds created with its native Electrum Seed Version System are exportable. " +
                                "If you have the mnemonic words, create a new wallet with a BIP39 keystore.");
                    } else if(ek.seed != null) {
                        keystore.setSource(KeystoreSource.SW_SEED);
                        String mnemonic = ek.seed;
                        String passphrase = ek.passphrase;
                        if(password != null) {
                            mnemonic = decrypt(mnemonic, password);
                            passphrase = decrypt(passphrase, password);
                        }

                        keystore.setSeed(new DeterministicSeed(mnemonic, passphrase, 0, DeterministicSeed.Type.ELECTRUM));

                        //Ensure the derivation path from the seed matches the provided xpub
                        String[] possibleDerivations = {"m", "m/0", "m/0'"};
                        for(String possibleDerivation : possibleDerivations) {
                            List<ChildNumber> derivation = KeyDerivation.parsePath(possibleDerivation);
                            DeterministicKey derivedKey = keystore.getExtendedMasterPrivateKey().getKey(derivation);
                            DeterministicKey derivedKeyPublicOnly = derivedKey.dropPrivateBytes().dropParent();
                            ExtendedKey xpub = new ExtendedKey(derivedKeyPublicOnly, derivedKey.getParentFingerprint(), derivation.isEmpty() ? ChildNumber.ZERO : derivation.get(derivation.size() - 1));
                            if(xpub.equals(xPub)) {
                                derivationPath = possibleDerivation;
                                break;
                            }
                        }
                    } else {
                        keystore.setSource(KeystoreSource.SW_WATCH);
                    }
                    keystore.setWalletModel(WalletModel.ELECTRUM);
                }

                keystore.setKeyDerivation(new KeyDerivation(masterFingerprint, derivationPath));
                keystore.setExtendedPublicKey(xPub);
                keystore.setLabel(ek.label != null ? ek.label : "Electrum");
                if(keystore.getLabel().length() > Keystore.MAX_LABEL_LENGTH) {
                    keystore.setLabel(keystore.getLabel().substring(0, Keystore.MAX_LABEL_LENGTH));
                }

                wallet.makeLabelsUnique(keystore);
                wallet.getKeystores().add(keystore);

                ExtendedKey.Header xpubHeader = ExtendedKey.Header.fromExtendedKey(ek.xpub);
                scriptType = xpubHeader.getDefaultScriptType();
            }

            wallet.setScriptType(scriptType);

            if(ew.wallet_type.equals("standard")) {
                wallet.setPolicyType(PolicyType.SINGLE);
                wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, scriptType, wallet.getKeystores(), 1));
            } else if(ew.wallet_type.contains("of")) {
                wallet.setPolicyType(PolicyType.MULTI);
                String[] mOfn = ew.wallet_type.split("of");
                int threshold = Integer.parseInt(mOfn[0]);
                wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.MULTI, scriptType, wallet.getKeystores(), threshold));
            } else {
                throw new ImportException("Unknown Electrum wallet type of " + ew.wallet_type);
            }

            if(ew.gap_limit != null) {
                wallet.setGapLimit(ew.gap_limit);
            }

            for(String key : ew.labels.keySet()) {
                try {
                    Sha256Hash txHash = Sha256Hash.wrap(key);
                    BlockTransaction blockTransaction = ew.transactions.get(txHash);
                    if(blockTransaction != null) {
                        blockTransaction.setLabel(ew.labels.get(key));
                    }
                } catch(Exception e) {
                    //not a tx - try an address
                    if(ew.addresses != null) {
                        try {
                            Address address = Address.fromString(key);
                            Map<KeyPurpose, List<String>> keyPurposes = Map.of(KeyPurpose.RECEIVE, ew.addresses.receiving, KeyPurpose.CHANGE, ew.addresses.change);
                            for(KeyPurpose keyPurpose : keyPurposes.keySet()) {
                                WalletNode purposeNode = wallet.getNode(keyPurpose);
                                purposeNode.fillToIndex(keyPurposes.get(keyPurpose).size() - 1);
                                for(WalletNode addressNode : purposeNode.getChildren()) {
                                    if(address.equals(addressNode.getAddress())) {
                                        addressNode.setLabel(ew.labels.get(key));
                                    }
                                }
                            }

                            for(BlockTransaction blkTx : ew.transactions.values()) {
                                if(blkTx.getLabel() == null) {
                                    Transaction tx = blkTx.getTransaction();
                                    for(TransactionOutput txOutput : tx.getOutputs()) {
                                        try {
                                            Address[] addresses = txOutput.getScript().getToAddresses();
                                            if(Arrays.asList(addresses).contains(address)) {
                                                blkTx.setLabel(ew.labels.get(key));
                                            }
                                        } catch(NonStandardScriptException ex) {
                                            //ignore
                                        }
                                    }
                                }
                            }
                        } catch(Exception ex) {
                            //not an address
                        }
                    }
                }
            }

            wallet.updateTransactions(ew.transactions);

            try {
                wallet.checkWallet();
            } catch(InvalidWalletException e) {
                throw new IllegalStateException("Imported Electrum wallet was invalid: " + e.getMessage());
            }

            return wallet;
        } catch (Exception e) {
            throw new ImportException("Error importing Electrum Wallet", e);
        }
    }

    private String decrypt(String encrypted, String password) {
        if(encrypted == null) {
            return null;
        }

        KeyDeriver keyDeriver = new DoubleSha256KeyDeriver();
        Key key = keyDeriver.deriveKey(password);
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);

        KeyCrypter keyCrypter = new AESKeyCrypter();
        byte[] initializationVector = Arrays.copyOfRange(encryptedBytes, 0, 16);
        byte[] cipher = Arrays.copyOfRange(encryptedBytes, 16, encryptedBytes.length);
        EncryptedData data = new EncryptedData(initializationVector, cipher, null, keyDeriver.getDeriverType(), keyCrypter.getCrypterType());
        byte[] decrypted = keyCrypter.decrypt(data, key);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @Override
    public String getWalletImportDescription() {
        return "Import an Electrum wallet.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return wallet.isEncrypted() ? "" : "json";
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        try {
            ElectrumJsonWallet ew = new ElectrumJsonWallet();
            if(wallet.getPolicyType().equals(PolicyType.SINGLE)) {
                ew.wallet_type = "standard";
            } else if(wallet.getPolicyType().equals(PolicyType.MULTI)) {
                ew.wallet_type = wallet.getDefaultPolicy().getNumSignaturesRequired() + "of" + wallet.getKeystores().size();
            } else {
                throw new ExportException("Could not export a wallet with a " + wallet.getPolicyType() + " policy");
            }

            ExtendedKey.Header xpubHeader = ExtendedKey.Header.fromScriptType(wallet.getScriptType(), false);
            ExtendedKey.Header xprvHeader = ExtendedKey.Header.fromScriptType(wallet.getScriptType(), true);

            int index = 1;
            for(Keystore keystore : wallet.getKeystores()) {
                ElectrumKeystore ek = new ElectrumKeystore();

                if(keystore.getSource() == KeystoreSource.HW_USB || keystore.getSource() == KeystoreSource.HW_AIRGAPPED) {
                    ek.label = keystore.getLabel();
                    ek.derivation = keystore.getKeyDerivation().getDerivationPath();
                    ek.root_fingerprint = keystore.getKeyDerivation().getMasterFingerprint();
                    ek.xpub = keystore.getExtendedPublicKey().toString(xpubHeader);
                    ek.type = "hardware";
                    ek.hw_type = keystore.getWalletModel().getType();
                    ew.use_encryption = false;
                } else if(keystore.getSource() == KeystoreSource.SW_SEED) {
                    ek.type = "bip32";
                    ek.xpub = keystore.getExtendedPublicKey().toString(xpubHeader);
                    ek.xprv = keystore.getExtendedPrivateKey().toString(xprvHeader);
                    ek.pw_hash_version = 1;
                    if(keystore.getSeed() == null || keystore.getSeed().getType() == DeterministicSeed.Type.BIP39) {
                        ew.seed_type = "bip39";
                    } else if(keystore.getSeed().getType() == DeterministicSeed.Type.ELECTRUM) {
                        ek.seed = keystore.getSeed().getMnemonicString().asString();
                        ek.passphrase = keystore.getSeed().getPassphrase() == null ? null : keystore.getSeed().getPassphrase().asString();
                    }
                    if(password != null) {
                        ek.xprv = encrypt(ek.xprv, password);
                        if(ek.seed != null) {
                            ek.seed = encrypt(ek.seed, password);
                        }
                        if(ek.passphrase != null) {
                            ek.passphrase = encrypt(ek.passphrase, password);
                        }
                        ew.use_encryption = true;
                    } else {
                        ew.use_encryption = false;
                    }
                } else if(keystore.getSource() == KeystoreSource.SW_WATCH) {
                    ek.type = "bip32";
                    ek.xpub = keystore.getExtendedPublicKey().toString(xpubHeader);
                    ek.pw_hash_version = 1;
                    ew.use_encryption = false;
                } else {
                    throw new ExportException("Cannot export a keystore of source " + keystore.getSource());
                }

                if(wallet.getPolicyType().equals(PolicyType.SINGLE)) {
                    ew.keystores.put("keystore", ek);
                } else if(wallet.getPolicyType().equals(PolicyType.MULTI)) {
                    ew.keystores.put("x" + index + "/", ek);
                }

                index++;
            }

            Gson gson = new Gson();
            JsonObject eJson = gson.toJsonTree(ew.keystores).getAsJsonObject();
            eJson.addProperty("wallet_type", ew.wallet_type);
            if(ew.use_encryption != null) {
                eJson.addProperty("use_encryption", ew.use_encryption);
            }
            if(ew.seed_type != null) {
                eJson.addProperty("seed_type", ew.seed_type);
            }

            gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            String json = gson.toJson(eJson);
            if(password != null) {
                ECKey encryptionKey = Pbkdf2KeyDeriver.DEFAULT_INSTANCE.deriveECKey(password);
                outputStream = new DeflaterOutputStream(new ECIESOutputStream(outputStream, encryptionKey));
            }

            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            log.error("Error exporting Electrum Wallet", e);
            throw new ExportException("Error exporting Electrum Wallet", e);
        }
    }

    private String encrypt(String plain, String password) throws NoSuchAlgorithmException {
        if(plain == null) {
            return null;
        }

        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);

        KeyDeriver keyDeriver = new DoubleSha256KeyDeriver();
        Key key = keyDeriver.deriveKey(password);

        KeyCrypter keyCrypter = new AESKeyCrypter();
        byte[] initializationVector = new byte[16];
        SecureRandom.getInstanceStrong().nextBytes(initializationVector);
        EncryptedData encryptedData = keyCrypter.encrypt(plainBytes, initializationVector, key);
        byte[] encrypted = new byte[initializationVector.length + encryptedData.getEncryptedBytes().length];
        System.arraycopy(initializationVector, 0, encrypted, 0, 16);
        System.arraycopy(encryptedData.getEncryptedBytes(), 0, encrypted, 16, encryptedData.getEncryptedBytes().length);
        byte[] encryptedBase64 = Base64.getEncoder().encode(encrypted);
        return new String(encryptedBase64, StandardCharsets.UTF_8);
    }

    @Override
    public boolean isEncrypted(File file) {
        return (FileType.BINARY.equals(IOUtils.getFileType(file)));
    }

    @Override
    public boolean isWalletImportScannable() {
        return false;
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return false;
    }

    @Override
    public boolean isWalletExportScannable() {
        return false;
    }

    @Override
    public String getWalletExportDescription() {
        return "Export this wallet as an Electrum wallet file.";
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return true;
    }

    private static class ElectrumJsonWallet {
        public Map<String, ElectrumKeystore> keystores = new LinkedHashMap<>();
        public String wallet_type;
        public String seed_type;
        public Boolean use_encryption;
        public ElectrumAddresses addresses;
        public Integer gap_limit;
        public Map<String, String> labels = new LinkedHashMap<>();
        public Map<Sha256Hash, BlockTransaction> transactions = new HashMap<>();
    }

    public static class ElectrumKeystore {
        public String xpub;
        public String xprv;
        public String hw_type;
        public String ckcc_xfp;
        public String root_fingerprint;
        public String label;
        public String soft_device_id;
        public String type;
        public String derivation;
        public String seed;
        public String passphrase;
        public Integer pw_hash_version;
    }

    public static class ElectrumAddresses {
        public List<String> receiving;
        public List<String> change;
    }
}
