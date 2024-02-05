package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.SchnorrSignature;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.psbt.PSBTInputSigner;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.control.CardImportPane;
import com.sparrowwallet.sparrow.io.CardApi;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class SatoCardApi extends CardApi {
    private static final Logger log = LoggerFactory.getLogger(SatoCardApi.class);

    private final WalletModel cardType;
    private final SatochipCommandSet cardProtocol;
    private final String pin;
    private String basePath = null;

    public SatoCardApi(WalletModel cardType, String pin) throws CardException {
        this.cardType = cardType;
        this.cardProtocol = new SatochipCommandSet();
        this.pin = pin;
    }

    @Override
    public boolean isInitialized() throws CardException {
        SatoCardStatus cardStatus = this.getStatus();
        return cardStatus.isInitialized(); // setupDone && isSeeded
    }

    //TODO
    @Override
    public void initialize(int slot, byte[] seedBytes) throws CardException {
        // TODO check device certificate
        SatoCardStatus cardStatus = this.getStatus();

        APDUResponse rapdu;
        if(!cardStatus.isSetupDone()) {
            byte maxPinTries = 5;
            rapdu = this.cardProtocol.cardSetup(maxPinTries, pin.getBytes(StandardCharsets.UTF_8));
            // check ok
        }

        if(!cardStatus.isSeeded()) {
            // check pin
            rapdu = this.cardProtocol.cardVerifyPIN(0, pin);
            // todo: check PIN response

            rapdu = this.cardProtocol.cardBip32ImportSeed(seedBytes);
            // check ok
        }
    }

    @Override
    public WalletModel getCardType() throws CardException {
        return WalletModel.SATOCHIP;
    }

    //TODO
    @Override
    public int getCurrentSlot() throws CardException {
        throw new CardException("Satochip does not support slots");
    }

    //TODO
    @Override
    public ScriptType getDefaultScriptType() {
        return ScriptType.P2WPKH;
    }

    SatoCardStatus getStatus() throws CardException {
        return this.cardProtocol.getApplicationStatus();
    }

    @Override
    public Service<Void> getAuthDelayService() throws CardException {
        return null;
    }

    @Override
    public boolean requiresBackup() throws CardException {
        return false;
    }

    @Override
    public Service<String> getBackupService() {
        return null;
    }

    @Override
    public boolean changePin(String newPin) throws CardException {
        this.cardProtocol.cardChangePIN((byte) 0, this.pin, newPin);
        return true;
    }

    void setDerivation(List<ChildNumber> derivation) throws CardException {
        this.basePath = KeyDerivation.writePath(derivation);
    }

    @Override
    public Service<Void> getInitializationService(byte[] seedBytes, StringProperty messageProperty) {
        return new CardInitializationService(seedBytes, messageProperty);

    }

    @Override
    public Service<Keystore> getImportService(List<ChildNumber> derivation, StringProperty messageProperty) {
        return new CardImportPane.CardImportService(new Satochip(), pin, derivation, messageProperty);
    }

    /*
     * Satochip derives BIP32 keys based on the fullPath (from masterseed to leaf), not the partial path from a given xpub.
     * the basePath (from masterseed to xpub) is only provided in Satochip.java:getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty)
     * In SatoCardApi:getKeystore(), no derivation path (i.e. basePath from masterSeed to xpub or relative path) is given and no derivation is reliably available as a object field.
     * currently, we try to get the path from this.basePath if available (or use a default value) but it's not reliable enough
     */
    @Override
    public Keystore getKeystore() throws CardException {
        this.cardProtocol.cardVerifyPIN(0, pin);
        String keyDerivationString = (this.basePath != null ? this.basePath : getDefaultScriptType().getDefaultDerivationPath());
        ExtendedKey.Header xtype = Network.get().getXpubHeader();
        String xpub = this.cardProtocol.cardBip32GetXpub(keyDerivationString, xtype);
        ExtendedKey extendedKey = ExtendedKey.fromDescriptor(xpub);
        String masterXpub = this.cardProtocol.cardBip32GetXpub("m", xtype);
        ExtendedKey masterExtendedKey = ExtendedKey.fromDescriptor(masterXpub);
        String masterFingerprint = Utils.bytesToHex(masterExtendedKey.getKey().getFingerprint());
        KeyDerivation keyDerivation = new KeyDerivation(masterFingerprint, keyDerivationString);

        Keystore keystore = new Keystore();
        keystore.setLabel(WalletModel.SATOCHIP.toDisplayString());
        keystore.setKeyDerivation(keyDerivation);
        keystore.setSource(KeystoreSource.HW_USB);
        keystore.setExtendedPublicKey(extendedKey);
        keystore.setWalletModel(WalletModel.SATOCHIP);

        return keystore;
    }

    @Override
    public Service<PSBT> getSignService(Wallet wallet, PSBT psbt, StringProperty messageProperty) {
        return new SignService(wallet, psbt, messageProperty);
    }

    void sign(Wallet wallet, PSBT psbt) throws CardException {
        Map<PSBTInput, WalletNode> signingNodes = wallet.getSigningNodes(psbt);
        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            if(!psbtInput.isSigned()) {
                WalletNode signingNode = signingNodes.get(psbtInput);
                String fullPath = null;
                List<Keystore> keystores = wallet.getKeystores();
                for(int i = 0; i < keystores.size(); i++) {
                    Keystore keystore = keystores.get(i);
                    WalletModel walletModel = keystore.getWalletModel();
                    if(walletModel == WalletModel.SATOCHIP) {
                        String basePath = keystore.getKeyDerivation().getDerivationPath();
                        String extendedPath = signingNode.getDerivationPath().substring(1);
                        fullPath = basePath + extendedPath;
                        keystore.getPubKey(signingNode);
                        break;
                    }
                }

                psbtInput.sign(new CardPSBTInputSigner(signingNode, fullPath));
            }
        }
    }

    @Override
    public Service<String> getSignMessageService(String message, ScriptType scriptType, List<ChildNumber> derivation, StringProperty messageProperty) {
        return new SignMessageService(message, scriptType, derivation, messageProperty);
    }

    String signMessage(String message, ScriptType scriptType, List<ChildNumber> derivation) throws CardException {
        String fullpath = KeyDerivation.writePath(derivation);
        cardProtocol.cardVerifyPIN(0, pin);

        // 2FA is optional, currently not supported in sparrow as it requires to send 2FA to a mobile app through a server.
        SatoCardStatus cardStatus = this.getStatus();
        if(cardStatus.needs2FA()) {
            throw new CardException("Satochip 2FA is not (yet) supported within Sparrow");
        }

        // derive the correct key in satochip
        APDUResponse rapdu = cardProtocol.cardBip32GetExtendedKey(fullpath);
        // recover pubkey
        SatochipParser parser = new SatochipParser();
        byte[][] extendeKeyBytes = parser.parseBip32GetExtendedKey(rapdu);
        ECKey pubkey = ECKey.fromPublicOnly(extendeKeyBytes[0]);

        // sign msg
        return pubkey.signMessage(message, scriptType, hash -> {
            try {
                // do the signature with satochip
                byte keynbr = (byte) 0xFF;
                byte[] chalresponse = null;
                APDUResponse rapdu2 = cardProtocol.cardSignTransactionHash(keynbr, hash.getBytes(), chalresponse);
                byte[] sigBytes = rapdu2.getData();
                return ECDSASignature.decodeFromDER(sigBytes);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Service<ECKey> getPrivateKeyService(Integer slot, StringProperty messageProperty) {
        throw new UnsupportedOperationException("Satochip does not support private key export");
    }

    @Override
    public Service<Address> getAddressService(StringProperty messageProperty) {
        return null;
    }

    @Override
    public void disconnect() {
        cardProtocol.cardDisconnect();
    }

    public class CardInitializationService extends Service<Void> {
        private final byte[] seedBytes;
        private final StringProperty messageProperty;

        public CardInitializationService(byte[] seedBytes, StringProperty messageProperty) {
            this.seedBytes = seedBytes;
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() throws Exception {
                    if(seedBytes == null) {
                        throw new CardException("Failed to initialize Satochip - no seed provided");
                    }

                    initialize(0, seedBytes);
                    return null;
                }
            };
        }
    }

    public class SignService extends Service<PSBT> {
        private final Wallet wallet;
        private final PSBT psbt;
        private final StringProperty messageProperty;

        public SignService(Wallet wallet, PSBT psbt, StringProperty messageProperty) {
            this.wallet = wallet;
            this.psbt = psbt;
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<PSBT> createTask() {
            return new Task<>() {
                @Override
                protected PSBT call() throws Exception {
                    sign(wallet, psbt);
                    return psbt;
                }
            };
        }
    }

    private class CardPSBTInputSigner implements PSBTInputSigner {
        private final WalletNode signingNode;
        private final String fullPath;
        private ECKey pubkey;

        // todo: provide derivationpath instead of WalletNode??
        public CardPSBTInputSigner(WalletNode signingNode, String fullPath) {
            this.signingNode = signingNode;
            this.fullPath = fullPath;
        }

        @Override
        public TransactionSignature sign(Sha256Hash hash, SigHash sigHash, TransactionSignature.Type signatureType) {
            try {
                // 2FA is optional, currently not supported in sparrow as it requires to send 2FA to a mobile app through a server.
                SatoCardStatus cardStatus = getStatus();
                if(cardStatus.needs2FA()) {
                    throw new CardException("Satochip 2FA is not (yet) supported within Sparrow");
                }

                // verify PIN
                APDUResponse rapdu0 = cardProtocol.cardVerifyPIN(0, pin);

                // derive the correct key in satochip and recover pubkey
                APDUResponse rapdu = cardProtocol.cardBip32GetExtendedKey(fullPath);
                SatochipParser parser = new SatochipParser();
                byte[][] extendeKeyBytes = parser.parseBip32GetExtendedKey(rapdu);
                ECKey internalPubkey = ECKey.fromPublicOnly(extendeKeyBytes[0]);

                if(signatureType == TransactionSignature.Type.ECDSA) {
                    // for ECDSA, pubkey is the same as internalPubkey
                    pubkey = internalPubkey;
                    // do the signature with satochip
                    byte keynbr = (byte) 0xFF;
                    byte[] chalresponse = null;
                    APDUResponse rapdu2 = cardProtocol.cardSignTransactionHash(keynbr, hash.getBytes(), chalresponse);
                    byte[] sigBytes = rapdu2.getData();
                    ECDSASignature ecdsaSig = ECDSASignature.decodeFromDER(sigBytes).toCanonicalised();
                    TransactionSignature txSig = new TransactionSignature(ecdsaSig, sigHash);

                    // verify
                    boolean isCorrect = pubkey.verify(hash, txSig);
                    return txSig;
                } else {
                    // Satochip supports schnorr signature only for version >= 0.14 !
                    byte[] versionBytes = cardStatus.getCardVersion();
                    int protocolVersion = versionBytes[0] * 256 + versionBytes[1];
                    if(protocolVersion < (256 * 0 + 14)) {
                        throw new CardException(WalletModel.SATOCHIP.toDisplayString() + " (with version below v0.14) cannot sign Taproot transactions");
                    }

                    // tweak the bip32 key according to bip341
                    byte keynbr = (byte) 0xFF;
                    byte[] tweak = null;
                    APDUResponse rapduTweak = cardProtocol.cardTaprootTweakPrivkey(keynbr, tweak);
                    byte[] tweakedPubkeyBytes = new byte[65];
                    System.arraycopy(rapduTweak.getData(), 2, tweakedPubkeyBytes, 0, 65);
                    pubkey = ECKey.fromPublicOnly(tweakedPubkeyBytes);

                    byte[] chalresponse = null;
                    APDUResponse rapdu2 = cardProtocol.cardSignSchnorrHash(keynbr, hash.getBytes(), chalresponse);
                    byte[] sigBytes = rapdu2.getData();
                    SchnorrSignature schnorrSig = SchnorrSignature.decode(sigBytes);
                    TransactionSignature txSig = new TransactionSignature(schnorrSig, sigHash);

                    // verify sig with outputPubkey...
                    boolean isCorrect2 = pubkey.verify(hash, txSig);

                    return txSig; //new TransactionSignature(schnorrSig, sigHash);
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ECKey getPubKey() {
            return pubkey;
        }
    }

    public class SignMessageService extends Service<String> {
        private final String message;
        private final ScriptType scriptType;
        private final List<ChildNumber> derivation;
        private final StringProperty messageProperty;

        public SignMessageService(String message, ScriptType scriptType, List<ChildNumber> derivation, StringProperty messageProperty) {
            this.message = message;
            this.scriptType = scriptType;
            this.derivation = derivation;
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                @Override
                protected String call() throws Exception {
                    return signMessage(message, scriptType, derivation);
                }
            };
        }
    }
}
