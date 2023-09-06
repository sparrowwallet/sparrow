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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
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
    private SatochipCommandSet cardProtocol;
    private String pin;
    private String basePath = null; // = "m/84'/1'/0'"

    public SatoCardApi(WalletModel cardType, String pin) throws CardException {
        log.debug("SATOCHIP SatoCardApi() cardType: " + cardType);
        this.cardType = cardType;
        this.cardProtocol = new SatochipCommandSet();
        this.pin = pin;
    }

    @Override
    public boolean isInitialized() throws CardException {
        log.debug("SATOCHIP SatoCardApi isInitialized() START");
        SatoCardStatus cardStatus = this.getStatus();
        return cardStatus.isInitialized(); // setupDone && isSeeded
    }

    //TODO
    @Override
    public void initialize(int slot, byte[] seedBytes) throws CardException {
        log.debug("SATOCHIP SatoCardApi initialize() START");
        // TODO check device certificate
        SatoCardStatus cardStatus = this.getStatus();

        APDUResponse rapdu;
        if (!cardStatus.isSetupDone()){
            byte maxPinTries = 5;
            rapdu  = this.cardProtocol.cardSetup(maxPinTries, pin.getBytes(StandardCharsets.UTF_8));
            // check ok
        }
        if (!cardStatus.isSeeded()){
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
        throw new CardException("Satochip does not support 'getCurrentSlot' !");
    }

    //TODO
    @Override
    public ScriptType getDefaultScriptType() {
        return ScriptType.P2WPKH;
    }

    SatoCardStatus getStatus() throws CardException {
        log.debug("SATOCHIP SatoCardApi getStatus() START");
        SatoCardStatus cardStatus = this.cardProtocol.getApplicationStatus();
        return cardStatus;
    }

    @Override
    public Service<Void> getAuthDelayService() throws CardException {
        log.debug("SATOCHIP SatoCardApi getAuthDelayService() START");
        return null;
    }

    @Override
    public boolean requiresBackup() throws CardException {
        log.debug("SATOCHIP SatoCardApi requiresBackup() START");
        return false; // todo?
    }

    @Override
    public Service<String> getBackupService() {
        log.debug("SATOCHIP SatoCardApi getBackupService() START");
        return null; //new BackupService();
    }

    @Override
    public boolean changePin(String newPin) throws CardException {
        log.debug("SATOCHIP SatoCardApi changePin() START");
        try{
            this.cardProtocol.cardChangePIN((byte)0, this.pin, newPin);
            return true;
        } catch(Exception e) {
            log.error("SATOCHIP SatoCardApi changePin() exception: " + e);
            return false;
        }
    }

    // TODO: remove?
    void setDerivation(List<ChildNumber> derivation) throws CardException {
        log.debug("SATOCHIP SatoCardApi setDerivation() START");
        // convert to string representation
        String derivationString = "m";
        for(int i=0;i<derivation.size();i++){
            derivationString += "/" + derivation.get(i).toString();
        }
        log.debug("SATOCHIP SatoCardApi setDerivation() derivationString: " + derivationString);
        // this basePath will be used when deriving keys
        this.basePath = KeyDerivation.writePath(derivation);
        log.debug("SATOCHIP SatoCardApi setDerivation() basePath: " + this.basePath);
    }

    @Override
    public Service<Void> getInitializationService(byte[] seedBytes, StringProperty messageProperty) {
        log.debug("SATOCHIP SatoCardApi getInitializationService() START");
        return new CardInitializationService(seedBytes, messageProperty);

    }

    @Override
    public Service<Keystore> getImportService(List<ChildNumber> derivation, StringProperty messageProperty) {
        log.debug("SATOCHIP SatoCardApi getImportService() START");
        log.debug("SATOCHIP SatoCardApi getImportService() derivation: " + derivation);
        return new CardImportPane.CardImportService(new Satochip(), pin, derivation, messageProperty);
    }

    /* todo: provide derivation path?
     * Satochip derives BIP32 keys based on the fullPath (from masterseed to leaf), not the partial path from a given xpub.
     * the basePath (from masterseed to xpub) is only provided in Satochip.java:getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty)
     * In SatoCardApi:getKeystore(), no derivation path (i.e. basePath from masterSeed to xpub or relative path) is given and no derivation is reliably available as a object field.
     * currently, we try to get the path from this.basePath if available (or use a default value) but it's not reliable enough
     */
    @Override
    public Keystore getKeystore() throws CardException {
        log.debug("SATOCHIP SatoCardApi getKeystore() START");

        APDUResponse rapdu = this.cardProtocol.cardVerifyPIN(0, pin);
        log.debug("SATOCHIP SatoCardApi getKeystore() cardVerifyPIN rapdu: " + Utils.bytesToHex(rapdu.getBytes()));

        String keyDerivationString = (this.basePath != null)? this.basePath : "m/84'/1'/0'";
        log.debug("SATOCHIP SatoCardApi getKeystore() keyDerivationString: " + keyDerivationString);

        ExtendedKey.Header xtype = Network.get().getXpubHeader();
        String xpub = this.cardProtocol.cardBip32GetXpub(keyDerivationString, xtype);
        log.debug("SATOCHIP SatoCardApi getKeystore() xpub: " + xpub);

        ExtendedKey extendedKey = ExtendedKey.fromDescriptor(xpub);
        log.debug("SATOCHIP SatoCardApi getKeystore() extendedKey: " + extendedKey);

        String masterFingerprint = Utils.bytesToHex(extendedKey.getKey().getFingerprint());
        log.debug("SATOCHIP SatoCardApi getKeystore() masterFingerprint: " + masterFingerprint);

        KeyDerivation keyDerivation = new KeyDerivation(masterFingerprint, keyDerivationString);
        log.debug("SATOCHIP SatoCardApi getKeystore() keyDerivation: " + keyDerivation);

        Keystore keystore = new Keystore();
        keystore.setLabel(WalletModel.SATOCHIP.toDisplayString());
        keystore.setKeyDerivation(keyDerivation);
        keystore.setSource(KeystoreSource.HW_USB);
        keystore.setExtendedPublicKey(extendedKey);
        keystore.setWalletModel(WalletModel.SATOCHIP);
        log.debug("SATOCHIP SatoCardApi getKeystore() keystore: " + keystore);
        return keystore;
    }

    @Override
    public Service<PSBT> getSignService(Wallet wallet, PSBT psbt, StringProperty messageProperty) {
        log.debug("SATOCHIP SatoCardApi getSignService() START");
        log.debug("SATOCHIP SatoCardApi getSignService() wallet: " + wallet);
        //log.debug("SATOCHIP SatoCardApi getSignService() psbt: " + psbt);
        return new SignService(wallet, psbt, messageProperty);
    }

    void sign(Wallet wallet, PSBT psbt) throws CardException {
        log.debug("SATOCHIP SatoCardApi sign() START");
        log.debug("SATOCHIP SatoCardApi sign() wallet: " + wallet);
        log.debug("SATOCHIP SatoCardApi sign() psbt: " + psbt);

        Map<PSBTInput, WalletNode> signingNodes = wallet.getSigningNodes(psbt);
        //log.debug("SATOCHIP SatoCardApi sign() signingNodes: " + signingNodes);
        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            if(!psbtInput.isSigned()) {
                WalletNode signingNode = signingNodes.get(psbtInput);
                log.debug("SATOCHIP SatoCardApi sign() signingNode: " + signingNode);
                log.debug("SATOCHIP SatoCardApi sign() signingNode.getDerivationPath(): " + signingNode.getDerivationPath()); // m/0/0
                try {
                    String fullPath= null;
                    List<Keystore> keystores = wallet.getKeystores();
                    log.debug("SATOCHIP SatoCardApi sign() keystores.size(): " + keystores.size());
                    for(int i=0;i<keystores.size();i++){
                        Keystore keystore = keystores.get(i);
                        log.debug("SATOCHIP SatoCardApi sign() i: " + i);
                        log.debug("SATOCHIP SatoCardApi sign() keystore.getLabel(): " + keystore.getLabel());
                        log.debug("SATOCHIP SatoCardApi sign() keystore.getSource(): " + keystore.getSource());
                        log.debug("SATOCHIP SatoCardApi sign() keystore.getWalletModel(): " + keystore.getWalletModel());
                        log.debug("SATOCHIP SatoCardApi sign() keystore.getKeyDerivation().getDerivationPath(): " + keystore.getKeyDerivation().getDerivationPath()); // m/66
                        log.debug("SATOCHIP SatoCardApi sign() keystore.getExtendedPublicKey(): " + keystore.getExtendedPublicKey());
                        WalletModel walletModel = keystore.getWalletModel();
                        if (walletModel==WalletModel.SATOCHIP){
                            String basePath = keystore.getKeyDerivation().getDerivationPath();
                            log.debug("SATOCHIP SatoCardApi sign() basePath: " + basePath);
                            String extendedPath= signingNode.getDerivationPath().substring(1);
                            log.debug("SATOCHIP SatoCardApi sign() extendedPath: " + extendedPath);
                            fullPath = basePath + extendedPath;
                            log.debug("SATOCHIP SatoCardApi sign() fullPath: " + fullPath);

                            ECKey keystorePubkey = keystore.getPubKey(signingNode);
                            log.debug("SATOCHIP SatoCardApi sign() keystore.getPubKey(signingNode): " + Utils.bytesToHex(keystorePubkey.getPubKey()));
                            break;
                        }
                    }
                    psbtInput.sign(new CardPSBTInputSigner(signingNode, fullPath));

                } finally {
                }
            }// endif
            else {
                log.debug("SATOCHIP SatoCardApi sign() psbtInput already signed!");
            }
        } // endfor
    }

    @Override
    public Service<String> getSignMessageService(String message, ScriptType scriptType, List<ChildNumber> derivation, StringProperty messageProperty) {
        log.debug("SATOCHIP SatoCardApi getSignMessageService() START");
        return new SignMessageService(message, scriptType, derivation, messageProperty);
    }

    String signMessage(String message, ScriptType scriptType, List<ChildNumber> derivation) throws CardException {
        log.debug("SATOCHIP SatoCardApi signMessage() START");
        log.debug("SATOCHIP SatoCardApi signMessage() message: " + message);
        log.debug("SATOCHIP SatoCardApi signMessage() scriptType: " + scriptType);
        log.debug("SATOCHIP SatoCardApi signMessage() derivation: " + derivation);
        String fullpath = KeyDerivation.writePath(derivation);
        log.debug("SATOCHIP SatoCardApi signMessage() fullpath: " + fullpath);

        try {
            APDUResponse rapdu0 = cardProtocol.cardVerifyPIN(0, pin);

            // 2FA is optionnal, currently not supported in sparrow as it requires to send 2FA to a mobile app through a server.
            SatoCardStatus cardStatus = this.getStatus();
            if (cardStatus.needs2FA()){
                throw new CardException("Satochip 2FA is not (yet) supported with Sparrow");
            }

            // derive the correct key in satochip
            APDUResponse rapdu = cardProtocol.cardBip32GetExtendedKey(fullpath);
            // recover pubkey
            SatochipParser parser= new SatochipParser();
            byte[][] extendeKeyBytes = parser.parseBip32GetExtendedKey(rapdu);
            ECKey pubkey = ECKey.fromPublicOnly(extendeKeyBytes[0]);
            log.debug("SATOCHIP SatoCardApi signMessage() pubkey: " + Utils.bytesToHex(extendeKeyBytes[0]));

            // sign msg
            return pubkey.signMessage(message, scriptType, hash -> {
                try{
                    // do the signature with satochip
                    byte keynbr = (byte)0xFF;
                    byte[] chalresponse = null;
                    APDUResponse rapdu2 = cardProtocol.cardSignTransactionHash(keynbr, hash.getBytes(), chalresponse);
                    byte[] sigBytes = rapdu2.getData();
                    log.debug("SATOCHIP SatoCardApi sign() sigBytes: " + Utils.bytesToHex(sigBytes));
                    ECDSASignature ecdsaSig = ECDSASignature.decodeFromDER(sigBytes);
                    return ecdsaSig;
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            //
        }
    }

    @Override
    public Service<ECKey> getPrivateKeyService(Integer slot, StringProperty messageProperty) {
        log.debug("SATOCHIP SatoCardApi getPrivateKeyService() START");
        throw new RuntimeException("Satochip does not support private key export!");
    }

    // todo: remove?
    ECKey getPrivateKey(int slot, int currentSlot) throws CardException {
        log.debug("SATOCHIP SatoCardApi getPrivateKey() START");
        throw new CardException("Satochip does not support 'getPrivateKey'!");
    }

    @Override
    public Service<Address> getAddressService(StringProperty messageProperty) {
        log.debug("SATOCHIP SatoCardApi getAddressService() START");
        // throw new runtimeException("Satochip does not support 'getAddress' currently!");
        return null; //new AddressService(messageProperty);
    }

    // todo: remove?
    Address getAddress(int currentSlot, int lastSlot, String addr) throws CardException {
        log.debug("SATOCHIP SatoCardApi getAddress() START");
        throw new CardException("Satochip does not support 'getAddress' currently!");
    }

    @Override
    public void disconnect() {
        log.debug("SATOCHIP SatoCardApi disconnect() START");
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
                    log.debug("SATOCHIP CardInitializationService createTask() START");
                    if (seedBytes == null){
                        // will show error message to user
                        log.debug("SATOCHIP CardInitializationService createTask() Error: seedBytes is null");
                        throw new Exception("Failed to initialize Satochip: " + messageProperty.get());
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
                    log.debug("SATOCHIP SatoCardApi.SignService createTask() START");
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
            log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() START");
            log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() hash:" + hash);
            log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() sigHash:" + sigHash);
            log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() signatureType:" + signatureType);

            try {
                log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() fullPath:" + this.fullPath);
                log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() signingNode.getDerivationPath():" + signingNode.getDerivationPath());

                // 2FA is optionnal, currently not supported in sparrow as it requires to send 2FA to a mobile app through a server.
                SatoCardStatus cardStatus = getStatus();
                if (cardStatus.needs2FA()){
                    throw new CardException("Satochip 2FA is not (yet) supported with Sparrow");
                }

                // verify PIN
                APDUResponse rapdu0 = cardProtocol.cardVerifyPIN(0, pin);

                // derive the correct key in satochip and recover pubkey
                APDUResponse rapdu = cardProtocol.cardBip32GetExtendedKey(fullPath);
                SatochipParser parser= new SatochipParser();
                byte[][] extendeKeyBytes = parser.parseBip32GetExtendedKey(rapdu);
                ECKey internalPubkey = ECKey.fromPublicOnly(extendeKeyBytes[0]);
                log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() pubkey: " + Utils.bytesToHex(extendeKeyBytes[0]));

                if(signatureType == TransactionSignature.Type.ECDSA) {
                    // for ECDSA, pubkey is the same as internalPubkey
                    pubkey = internalPubkey;
                    // do the signature with satochip
                    byte keynbr = (byte)0xFF;
                    byte[] chalresponse = null;
                    APDUResponse rapdu2 = cardProtocol.cardSignTransactionHash(keynbr, hash.getBytes(), chalresponse);
                    byte[] sigBytes = rapdu2.getData();
                    log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() sigBytes: " + Utils.bytesToHex(sigBytes));
                    ECDSASignature ecdsaSig = ECDSASignature.decodeFromDER(sigBytes).toCanonicalised();
                    TransactionSignature txSig = new TransactionSignature(ecdsaSig, sigHash);

                    // verify
                    boolean isCorrect = pubkey.verify(hash, txSig);
                    log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() ECDSA verify with pubkey: " + isCorrect);

                    return txSig;

                } else {
                    // Satochip supports schnorr signature only for version >= 0.14 !
                    byte[] versionBytes = cardStatus.getCardVersion();
                    int protocolVersion = versionBytes[0]*256 + versionBytes[1];
                    if (protocolVersion< (256*0+14) ){
                        throw new RuntimeException(WalletModel.SATOCHIP.toDisplayString() + " (with version below v0.14) cannot sign " + signatureType + " transactions!");
                    }

                    // tweak the bip32 key according to bip341
                    byte keynbr = (byte)0xFF;
                    byte[] tweak = null;
                    APDUResponse rapduTweak = cardProtocol.cardTaprootTweakPrivkey(keynbr, tweak);
                    log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() cardTaprootTweakPrivkey(): " + rapduTweak.toHexString());
                    byte[] tweakedPubkeyBytes= new byte[65];
                    System.arraycopy( rapduTweak.getData(), 2, tweakedPubkeyBytes, 0, 65 );
                    pubkey = ECKey.fromPublicOnly(tweakedPubkeyBytes);

                    byte[] chalresponse = null;
                    APDUResponse rapdu2 = cardProtocol.cardSignSchnorrHash(keynbr, hash.getBytes(), chalresponse);
                    byte[] sigBytes = rapdu2.getData();
                    log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() SCHNORR sigBytes: " + Utils.bytesToHex(sigBytes));
                    SchnorrSignature schnorrSig = SchnorrSignature.decode(sigBytes);
                    TransactionSignature txSig = new TransactionSignature(schnorrSig, sigHash);

                    // verify sig with outputPubkey...
                    boolean isCorrect2 = pubkey.verify(hash, txSig);
                    log.debug("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() SCHNORR verify with outputPubkey: " + isCorrect2);

                    return txSig; //new TransactionSignature(schnorrSig, sigHash);
                }

            } catch(Exception e) {
                e.printStackTrace();
                log.error("SATOCHIP SatoCardApi.CardPSBTInputSigner sign() Exception: " + e);
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
                    log.debug("SATOCHIP SatoCardApi.SignMessageService createTask() message: " + message);
                    log.debug("SATOCHIP SatoCardApi.SignMessageService createTask() scriptType: " + scriptType);
                    log.debug("SATOCHIP SatoCardApi.SignMessageService createTask() derivation: " + derivation);
                    return signMessage(message, scriptType, derivation);
                }
            };
        }
    }
}
