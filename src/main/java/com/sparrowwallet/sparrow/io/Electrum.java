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
import com.sparrowwallet.drongo.wallet.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
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
        return "Import a single keystore from an Electrum wallet (use File > Import > Electrum to import a multisig wallet)";
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        Wallet wallet = importWallet(inputStream, password);

        if(!wallet.getPolicyType().equals(PolicyType.SINGLE) || wallet.getKeystores().size() != 1) {
            throw new ImportException("Multisig wallet detected - import it using File > Import Wallet");
        }

        if(!wallet.getScriptType().equals(scriptType)) {
            //TODO: Derive appropriate ScriptType keystore from xprv if present
            throw new ImportException("Wallet has an incompatible script type of " + wallet.getScriptType() + ", and the correct script type cannot be derived without the master private key");
        }

        return wallet.getKeystores().get(0);
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        Reader reader;
        if(password != null) {
            ECKey decryptionKey = ECIESKeyCrypter.deriveECKey(password);
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
                        throw new ImportException("Wallet has keystore of unknown hardware wallet type \"" + ek.hw_type + "\"");
                    }
                } else if("bip32".equals(ek.type)) {
                    if(ek.xprv != null && ek.seed == null) {
                        throw new ImportException("Electrum does not support exporting BIP39 derived seeds.");
                    } else if(ek.seed != null) {
                        keystore.setSource(KeystoreSource.SW_SEED);
                        String seed = ek.seed;
                        String passphrase = ek.passphrase;
                        if(password != null) {
                            seed = decrypt(seed, password);
                            passphrase = decrypt(passphrase, password);
                        }

                        keystore.setSeed(new DeterministicSeed(seed, null, passphrase, 0, DeterministicSeed.Type.ELECTRUM));
                        keystore.getSeed().setPassphrase(passphrase);

                        if(derivationPath == "m/0") {
                            derivationPath = "m/0'";
                        }
                    } else {
                        keystore.setSource(KeystoreSource.SW_WATCH);
                    }
                    keystore.setWalletModel(WalletModel.ELECTRUM);
                }

                keystore.setKeyDerivation(new KeyDerivation(masterFingerprint, derivationPath));
                keystore.setExtendedPublicKey(xPub);
                keystore.setLabel(ek.label != null ? ek.label : "Electrum " + masterFingerprint);
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

            if(!wallet.isValid()) {
                throw new IllegalStateException("Electrum wallet is in an inconsistent state.");
            }

            return wallet;
        } catch (Exception e) {
            throw new ImportException(e);
        }
    }

    private String decrypt(String encrypted, String password) {
        byte[] passwordHash = Utils.sha256sha256(password.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        byte[] decrypted = Utils.decryptAesCbcPkcs7(Arrays.copyOfRange(encryptedBytes, 0, 16), Arrays.copyOfRange(encryptedBytes, 16, encryptedBytes.length), passwordHash);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @Override
    public String getWalletImportDescription() {
        return "Import an Electrum wallet";
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
                        ek.seed = keystore.getSeed().getMnemonicString();
                        ek.passphrase = keystore.getSeed().getPassphrase();
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
        return FileType.BINARY.equals(IOUtils.getFileType(file));
    }

    @Override
    public String getWalletExportDescription() {
        return "Export this wallet as an Electrum wallet file";
    }

    private static class ElectrumJsonWallet {
        public Map<String, ElectrumKeystore> keystores = new LinkedHashMap<>();
        public String wallet_type;
        public String seed_type;
        public Boolean use_encryption;
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
