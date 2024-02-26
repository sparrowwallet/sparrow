package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ColdcardMultisig implements WalletImport, KeystoreFileImport, WalletExport {
    private static final Logger log = LoggerFactory.getLogger(ColdcardMultisig.class);

    @Override
    public String getName() {
        return "Coldcard Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.COLDCARD;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        ColdcardKeystore cck = JsonPersistence.getGson().fromJson(reader, ColdcardKeystore.class);

        Keystore keystore = new Keystore("Coldcard");
        keystore.setSource(KeystoreSource.HW_AIRGAPPED);
        keystore.setWalletModel(WalletModel.COLDCARD);

        try {
            if(cck.xpub != null && cck.path != null) {
                ExtendedKey.Header header = ExtendedKey.Header.fromExtendedKey(cck.xpub);
                if(header.getDefaultScriptType() != scriptType) {
                    throw new ImportException("This wallet's script type (" + scriptType + ") does not match the " + getName() + " script type (" + header.getDefaultScriptType() + ")");
                }
                keystore.setKeyDerivation(new KeyDerivation(cck.xfp, cck.path));
                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(cck.xpub));
            } else if(scriptType.equals(ScriptType.P2SH)) {
                keystore.setKeyDerivation(new KeyDerivation(cck.xfp, cck.p2sh_deriv));
                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(cck.p2sh));
            } else if(scriptType.equals(ScriptType.P2SH_P2WSH)) {
                keystore.setKeyDerivation(new KeyDerivation(cck.xfp, cck.p2wsh_p2sh_deriv != null ? cck.p2wsh_p2sh_deriv : cck.p2sh_p2wsh_deriv));
                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(cck.p2wsh_p2sh != null ? cck.p2wsh_p2sh : cck.p2sh_p2wsh));
            } else if(scriptType.equals(ScriptType.P2WSH)) {
                keystore.setKeyDerivation(new KeyDerivation(cck.xfp, cck.p2wsh_deriv));
                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(cck.p2wsh));
            } else {
                throw new ImportException("Correct derivation not found for script type: " + scriptType);
            }
        } catch(NullPointerException e) {
            throw new ImportException("Correct derivation not found for script type: " + scriptType);
        }

        return keystore;
    }

    private static class ColdcardKeystore {
        public String p2sh_deriv;
        public String p2sh;
        public String p2wsh_p2sh_deriv;
        public String p2wsh_p2sh;
        public String p2sh_p2wsh_deriv;
        public String p2sh_p2wsh;
        public String p2wsh_deriv;
        public String p2wsh;
        public String xpub;
        public String path;
        public String xfp;
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import file created by using the Settings > Multisig Wallets > Export XPUB > " + account + " feature on your Coldcard.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "txt";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.MULTI);

        int threshold = 2;
        ScriptType scriptType = ScriptType.P2SH;
        String derivation = null;

        try {
            List<String> lines = CharStreams.readLines(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] keyValue = line.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    switch (key) {
                        case "Name":
                            wallet.setName(value.trim());
                            break;
                        case "Policy":
                            threshold = Integer.parseInt(value.split(" ")[0]);
                            break;
                        case "Derivation":
                        case "# derivation":
                            derivation = value;
                            break;
                        case "Format":
                            scriptType = ScriptType.valueOf(value.replace("P2WSH-P2SH", "P2SH_P2WSH"));
                            break;
                        default:
                            if (key.length() == 8 && Utils.isHex(key)) {
                                Keystore keystore = new Keystore("Coldcard");
                                keystore.setSource(KeystoreSource.HW_AIRGAPPED);
                                keystore.setWalletModel(WalletModel.COLDCARD);
                                keystore.setKeyDerivation(new KeyDerivation(key, derivation));
                                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(value));
                                wallet.makeLabelsUnique(keystore);
                                wallet.getKeystores().add(keystore);
                            }
                    }
                }
            }


            Policy policy = Policy.getPolicy(PolicyType.MULTI, scriptType, wallet.getKeystores(), threshold);
            wallet.setDefaultPolicy(policy);
            wallet.setScriptType(scriptType);

            if(!wallet.isValid()) {
                throw new IllegalStateException("This file does not describe a valid wallet. " + getKeystoreImportDescription());
            }

            return wallet;
        } catch(Exception e) {
            throw new ImportException("Error importing " + getName() + " wallet", e);
        }
    }

    @Override
    public String getWalletImportDescription() {
        return "Import file created by using the Settings > Multisig Wallets > [Wallet Detail] > Coldcard Export feature on your Coldcard.";
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        if(!wallet.isValid()) {
            throw new ExportException("Cannot export an incomplete wallet");
        }

        if(!wallet.getPolicyType().equals(PolicyType.MULTI)) {
            throw new ExportException(getName() + " import requires a multisig wallet");
        }

        boolean multipleDerivations = false;
        Set<String> derivationSet = new HashSet<>();
        for(Keystore keystore : wallet.getKeystores()) {
            derivationSet.add(keystore.getKeyDerivation().getDerivationPath());
        }
        if(derivationSet.size() > 1) {
            multipleDerivations = true;
        }

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.append("# " + getName() + " setup file (created by Sparrow)\n");
            writer.append("#\n");
            writer.append("Name: ").append(wallet.getFullName().length() >= 20 ? (wallet.getDisplayName().length() >= 20 ? wallet.getDisplayName().substring(0, 20) : wallet.getDisplayName()) : wallet.getFullName()).append("\n");
            writer.append("Policy: ").append(Integer.toString(wallet.getDefaultPolicy().getNumSignaturesRequired())).append(" of ").append(Integer.toString(wallet.getKeystores().size())).append("\n");
            if(!multipleDerivations) {
                writer.append("Derivation: ").append(wallet.getKeystores().get(0).getKeyDerivation().getDerivationPath()).append("\n");
            }
            writer.append("Format: ").append(wallet.getScriptType().toString().replace("P2SH-P2WSH", "P2WSH-P2SH")).append("\n");
            writer.append("\n");

            for(Keystore keystore : wallet.getKeystores()) {
                if(multipleDerivations) {
                    writer.append("Derivation: ").append(keystore.getKeyDerivation().getDerivationPath()).append("\n");
                }
                writer.append(keystore.getKeyDerivation().getMasterFingerprint().toUpperCase(Locale.ROOT)).append(": ").append(keystore.getExtendedPublicKey().toString()).append("\n");
                if(multipleDerivations) {
                    writer.append("\n");
                }
            }

            writer.flush();
        } catch(Exception e) {
            log.error("Error exporting " + getName() + " wallet", e);
            throw new ExportException("Error exporting " + getName() + " wallet", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Export file that can be read by your Coldcard using the Settings > Multisig Wallets > Import from File feature.";
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
    public boolean isKeystoreImportScannable() {
        return true;
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
