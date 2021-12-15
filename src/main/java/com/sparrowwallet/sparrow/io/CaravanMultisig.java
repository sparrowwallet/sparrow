package com.sparrowwallet.sparrow.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CaravanMultisig implements WalletImport, WalletExport {
    private static final Logger log = LoggerFactory.getLogger(ColdcardMultisig.class);

    @Override
    public String getName() {
        return "Caravan Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.CARAVAN;
    }

    @Override
    public String getWalletImportDescription() {
        return "Import the file created via the Download Wallet Details button in Caravan.";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        try {
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            CaravanFile cf = JsonPersistence.getGson().fromJson(reader, CaravanFile.class);

            Wallet wallet = new Wallet();
            wallet.setName(cf.name);
            wallet.setPolicyType(PolicyType.MULTI);
            ScriptType scriptType = ScriptType.valueOf(cf.addressType.replace('-', '_'));

            for(ExtPublicKey extKey : cf.extendedPublicKeys) {
                Keystore keystore = new Keystore(extKey.name);
                try {
                    keystore.setKeyDerivation(new KeyDerivation(extKey.xfp, extKey.bip32Path));
                } catch(NumberFormatException e) {
                    keystore.setKeyDerivation(new KeyDerivation(extKey.xfp, scriptType.getDefaultDerivationPath()));
                }

                keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(extKey.xpub));

                WalletModel walletModel = WalletModel.fromType(extKey.method);
                if(walletModel == null) {
                    keystore.setWalletModel(WalletModel.SPARROW);
                    keystore.setSource(KeystoreSource.SW_WATCH);
                } else {
                    keystore.setWalletModel(walletModel);
                    keystore.setSource(KeystoreSource.HW_USB);
                }
                wallet.getKeystores().add(keystore);
            }

            wallet.setScriptType(scriptType);
            wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.MULTI, scriptType, wallet.getKeystores(), cf.quorum.requiredSigners));

            return wallet;
        } catch(Exception e) {
            log.error("Error importing " + getName() + " wallet", e);
            throw new ImportException("Error importing " + getName() + " wallet", e);
        }
    }

    @Override
    public boolean isWalletImportScannable() {
        return false;
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream) throws ExportException {
        if(!wallet.isValid()) {
            throw new ExportException("Cannot export an incomplete wallet");
        }

        if(!wallet.getPolicyType().equals(PolicyType.MULTI)) {
            throw new ExportException(getName() + " import requires a multisig wallet");
        }

        try {
            CaravanFile cf = new CaravanFile();
            cf.name = wallet.getFullName();
            cf.addressType = wallet.getScriptType().toString().replace('-', '_');
            cf.network = Network.get().getName();
            cf.client = new Client();

            Quorum quorum = new Quorum();
            quorum.requiredSigners = wallet.getDefaultPolicy().getNumSignaturesRequired();
            quorum.totalSigners = wallet.getKeystores().size();
            cf.quorum = quorum;

            cf.extendedPublicKeys = new ArrayList<>();
            for(Keystore keystore : wallet.getKeystores()) {
                ExtPublicKey extKey = new ExtPublicKey();
                extKey.name = keystore.getLabel();
                extKey.bip32Path = keystore.getKeyDerivation().getDerivationPath();
                extKey.xpub = keystore.getExtendedPublicKey().toString();
                extKey.xfp = keystore.getKeyDerivation().getMasterFingerprint();
                extKey.method = keystore.getWalletModel().getType();
                cf.extendedPublicKeys.add(extKey);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            String json = gson.toJson(cf);
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch(Exception e) {
            log.error("Error exporting " + getName() + " wallet", e);
            throw new ExportException("Error exporting " + getName() + " wallet", e);
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Export a file that can be imported via the Import Wallet Configuration button in Caravan.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "json";
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

    private static final class CaravanFile {
        public String name;
        public String addressType;
        public String network;
        public Client client;
        public Quorum quorum;
        public List<ExtPublicKey> extendedPublicKeys;
        public int startingAddressIndex = 0;
    }

    private static final class Client {
        public String type = "public";
    }

    private static final class Quorum {
        public int requiredSigners;
        public int totalSigners;
    }

    private static final class ExtPublicKey {
        public String name;
        public String bip32Path;
        public String xpub;
        public String xfp;
        public String method;
    }
}
