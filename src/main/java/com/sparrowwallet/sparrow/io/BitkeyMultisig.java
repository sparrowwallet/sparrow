package com.sparrowwallet.sparrow.io;

import com.google.common.io.CharStreams;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BitkeyMultisig implements WalletImport, KeystoreFileImport {
    private static final Logger log = LoggerFactory.getLogger(BitkeyMultisig.class);

    @Override
    public String getName() {
        return "Bitkey Multisig";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.BITKEY;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream, String password) throws ImportException {
        throw new ImportException("Bitkey export format is for full wallets, not individual keystores in this context.");
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import the XPUB file exported from your Bitkey app. This file contains all co-signer xpubs for a multisig wallet.";
    }

    @Override
    public Wallet importWallet(InputStream inputStream, String password) throws ImportException {
        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.MULTI);
        wallet.setName(getName());

        try {
            List<String> lines = CharStreams.readLines(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String externalDescriptorLine = null;

            for (String line : lines) {
                if (line.startsWith("External: ")) {
                    externalDescriptorLine = line.substring("External: ".length()).trim();
                    break;
                }
            }

            if (externalDescriptorLine == null) {
                throw new ImportException("External descriptor line not found in Bitkey export file.");
            }

            String descriptorContent;
            if (externalDescriptorLine.startsWith("wsh(")) {
                wallet.setScriptType(ScriptType.P2WSH);
                descriptorContent = externalDescriptorLine.substring("wsh(".length(), externalDescriptorLine.length() - 1); // remove wsh() wrapper
            } else if (externalDescriptorLine.startsWith("sh(wsh(")) {
                wallet.setScriptType(ScriptType.P2SH_P2WSH);
                descriptorContent = externalDescriptorLine.substring("sh(wsh(".length(), externalDescriptorLine.length() - 2); // remove sh(wsh()) wrapper
            } else {
                throw new ImportException("Unsupported script type in Bitkey descriptor: " + externalDescriptorLine);
            }

            if (!descriptorContent.startsWith("sortedmulti(")) {
                throw new ImportException("Could not find sortedmulti in descriptor: " + descriptorContent);
            }

            String sortedMultiContent = descriptorContent.substring("sortedmulti(".length(), descriptorContent.length() - 1);
            
            int firstComma = sortedMultiContent.indexOf(',');
            if (firstComma == -1) {
                throw new ImportException("Could not parse threshold from sortedmulti: " + sortedMultiContent);
            }
            int threshold = Integer.parseInt(sortedMultiContent.substring(0, firstComma));
            
            String xpubsData = sortedMultiContent.substring(firstComma + 1);
            String[] xpubEntries = xpubsData.split(",");

            List<Keystore> keystores = new ArrayList<>();
            for (String entry : xpubEntries) {
                entry = entry.trim();
                if (!entry.startsWith("[") || !entry.contains("]")) {
                    throw new ImportException("Invalid xpub entry format: " + entry);
                }

                String fullDerivation = entry.substring(1, entry.indexOf(']'));
                String xpubWithBip32Path = entry.substring(entry.indexOf(']') + 1);
                
                int slashIndex = fullDerivation.indexOf('/');
                if (slashIndex == -1) {
                    throw new ImportException("Invalid full derivation format (missing fingerprint/path separator '/'): " + fullDerivation);
                }
                String fingerprint = fullDerivation.substring(0, slashIndex);
                String derivationPathSuffix = fullDerivation.substring(slashIndex + 1);
                // The KeyDerivation class expects 'm/' prefix if it's not already there implicitly by being a full path.
                // Since we have a suffix after the fingerprint, we prepend 'm/'.
                String derivationPath = "m/" + derivationPathSuffix;

                // The xpub from descriptor might include /0/* or /1/*, remove it for the base xpub used in Keystore.
                String baseXpub = xpubWithBip32Path;
                if (baseXpub.endsWith("/0/*")) {
                    baseXpub = baseXpub.substring(0, baseXpub.length() - "/0/*".length());
                } else if (baseXpub.endsWith("/1/*")) {
                    baseXpub = baseXpub.substring(0, baseXpub.length() - "/1/*".length());
                }

                Keystore keystore = new Keystore();
                keystore.setLabel(WalletModel.BITKEY.toDisplayString() + " " + fingerprint.substring(0, Math.min(fingerprint.length(), 4)));
                keystore.setSource(KeystoreSource.HW_AIRGAPPED);
                keystore.setWalletModel(WalletModel.BITKEY);
                keystore.setKeyDerivation(new KeyDerivation(fingerprint, derivationPath));

                log.debug("Attempting to create ExtendedKey from baseXpub: [{}] for fingerprint: [{}] and path: [{}]", baseXpub, fingerprint, derivationPath);
                try {
                    keystore.setExtendedPublicKey(ExtendedKey.fromDescriptor(baseXpub));
                } catch (Exception e) {
                    log.error("Failed to create ExtendedKey from baseXpub: {}", baseXpub, e);
                    throw new ImportException("Failed to parse xpub: " + baseXpub, e);
                }
                wallet.makeLabelsUnique(keystore);
                keystores.add(keystore);
            }

            if (keystores.isEmpty()) {
                throw new ImportException("No xpubs found in Bitkey descriptor: " + externalDescriptorLine);
            }
            wallet.getKeystores().addAll(keystores);

            Policy policy = Policy.getPolicy(PolicyType.MULTI, wallet.getScriptType(), wallet.getKeystores(), threshold);
            wallet.setDefaultPolicy(policy);

            log.debug("Wallet assembled. Policy: {} ScriptType: {} Keystores: {} Threshold: {}", policy, wallet.getScriptType(), keystores.size(), threshold);

            return wallet;
        } catch (Exception e) {
            log.error("Error importing Bitkey Multisig wallet", e);
            throw new ImportException("Error importing Bitkey Multisig wallet: " + e.getMessage(), e);
        }
    }

    @Override
    public String getWalletImportDescription() {
        return "Import the .txt file exported from your Bitkey companion app. This file describes a multisig wallet setup.";
    }

    @Override
    public boolean isEncrypted(File file) {
        return false;
    }

    @Override
    public boolean isWalletImportScannable() {
        return false;
    }

    @Override
    public boolean isKeystoreImportScannable() {
        return false;
    }
} 