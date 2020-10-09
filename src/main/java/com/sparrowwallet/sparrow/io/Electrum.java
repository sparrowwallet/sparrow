package com.sparrowwallet.sparrow.io;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

public class Electrum implements KeystoreFileImport, WalletImport, WalletExport {
    @Override
    public String getName() {
        return "Electrum";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.ELECTRUM;
    }

    @Override
    public String getKeystoreImportDescription() {
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
            reader = new InputStreamReader(new InflaterInputStream(new ECIESInputStream(inputStream, decryptionKey)));
        } else {
            reader = new InputStreamReader(inputStream);
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
                        ek.root_fingerprint = Utils.bytesToHex(le).toUpperCase();
                    }
                    ew.keystores.put(key, ek);
                }

                if(key.equals("labels")) {
                    JsonObject jsonObject = (JsonObject)map.get(key);
                    for(String labelKey : jsonObject.keySet()) {
                        ew.labels.put(labelKey, jsonObject.get(labelKey).getAsString());
                    }
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
                        if(derivationPath.equals("m/0")) {
                            derivationPath = "m/0'";
                        }
                    } else {
                        keystore.setSource(KeystoreSource.SW_WATCH);
                    }
                    keystore.setWalletModel(WalletModel.ELECTRUM);
                }

                keystore.setKeyDerivation(new KeyDerivation(masterFingerprint, derivationPath));
                keystore.setExtendedPublicKey(xPub);
                keystore.setLabel(ek.label != null ? ek.label : "Electrum");
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

            for(String key : ew.labels.keySet()) {
                try {
                    Sha256Hash txHash = Sha256Hash.wrap(key);
                    BlockTransaction blockTransaction = ew.transactions.get(txHash);
                    if(blockTransaction != null) {
                        blockTransaction.setLabel(ew.labels.get(key));
                    }
                } catch(IllegalArgumentException e) {
                    //not a tx, probably an address
                }
            }

            wallet.updateTransactions(ew.transactions);

            if(!wallet.isValid()) {
                throw new IllegalStateException("Electrum wallet is in an inconsistent state.");
            }

            return wallet;
        } catch (Exception e) {
            throw new ImportException(e);
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
    public String getExportFileExtension() {
        return "json";
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream) throws ExportException {
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
                    if(keystore.getSeed().getType() == DeterministicSeed.Type.ELECTRUM) {
                        ek.seed = keystore.getSeed().getMnemonicString().asString();
                        ek.passphrase = keystore.getSeed().getPassphrase() == null ? null : keystore.getSeed().getPassphrase().asString();
                    } else if(keystore.getSeed().getType() == DeterministicSeed.Type.BIP39) {
                        ew.seed_type = "bip39";
                    }
                    ew.use_encryption = false;
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
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            throw new ExportException(e);
        }
    }

    @Override
    public boolean isEncrypted(File file) {
        return (FileType.BINARY.equals(IOUtils.getFileType(file)));
    }

    @Override
    public boolean isScannable() {
        return false;
    }

    @Override
    public String getWalletExportDescription() {
        return "Export this wallet as an Electrum wallet file.";
    }

    private static class ElectrumJsonWallet {
        public Map<String, ElectrumKeystore> keystores = new LinkedHashMap<>();
        public String wallet_type;
        public String seed_type;
        public Boolean use_encryption;
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
}
