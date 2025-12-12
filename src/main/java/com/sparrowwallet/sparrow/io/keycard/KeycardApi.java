package com.sparrowwallet.sparrow.io.keycard;

import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.ECKey;
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

import javax.smartcardio.CardException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class KeycardApi extends CardApi {
    private static final Logger log = LoggerFactory.getLogger(KeycardApi.class);

    private final WalletModel cardType;
    private final KeycardTransport cardTransport;
    private final KeycardCommandSet cardProtocol;
    private final String pin;
    private String basePath = null;

    public KeycardApi(WalletModel cardType, String pin) throws CardException {
        this.cardType = cardType;
        this.cardTransport = new KeycardTransport(Identifiers.getKeycardInstanceAID());
        this.cardProtocol = new KeycardCommandSet(cardTransport);
        this.pin = pin;

        try {
            this.cardProtocol.select().checkOK();
        } catch(IOException | APDUException e) {
            throw new CardException(e);
        }
    }

    @Override
    public boolean isInitialized() throws CardException {
        return getStatus().hasMasterKey();
    }

    //TODO
    @Override
    public void initialize(int slot, byte[] seedBytes) throws CardException {
        // TODO check device certificate
        ApplicationInfo cardStatus = this.getStatus();

        if(!cardStatus.isInitializedCard()) {
            try {
                String puk = String.format("%012d", new SecureRandom().nextLong(999999999999L));
                this.cardProtocol.init(pin, puk, "KeycardDefaultPairing").checkOK();
                this.cardProtocol.select().checkOK();
            } catch(IOException | APDUException e) {
                throw new CardException(e);
            }
        }

        if(!cardStatus.hasMasterKey()) {
            try {
                this.authenticate();
                this.cardProtocol.autoUnpair();
                this.cardProtocol.loadKey(seedBytes).checkOK();
            } catch(IOException | APDUException e) {
                throw new CardException(e);
            }
        }
    }

    @Override
    public WalletModel getCardType() throws CardException {
        return WalletModel.KEYCARD;
    }

    @Override
    public int getCurrentSlot() throws CardException {
        throw new CardException("Keycard does not support slots");
    }

    @Override
    public ScriptType getDefaultScriptType() {
        return ScriptType.P2WPKH;
    }

    ApplicationInfo getStatus() {
        return this.cardProtocol.getApplicationInfo();
    }

    void authenticate() throws IOException, APDUException {
        this.cardProtocol.autoPair("KeycardDefaultPairing");
        this.cardProtocol.autoOpenSecureChannel();
        this.cardProtocol.verifyPIN(pin).checkOK();
        this.cardProtocol.autoUnpair();
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
        try {
            this.authenticate();
            this.cardProtocol.changePIN(newPin).checkOK();
        } catch(IOException | APDUException e) {
            throw new CardException(e);
        }
        return true;
    }

    void setDerivation(List<ChildNumber> derivation) throws CardException {
        this.basePath = KeyDerivation.writePath(derivation);
    }

    @Override
    public Service<Void> getInitializationService(byte[] seedBytes, StringProperty messageProperty) {
        return new KeycardApi.CardInitializationService(seedBytes, messageProperty);

    }

    @Override
    public Service<Keystore> getImportService(List<ChildNumber> derivation, StringProperty messageProperty) {
        return new CardImportPane.CardImportService(new Keycard(), pin, derivation, messageProperty);
    }

    private byte[] compressedPub(byte[] uncompressedPub) {
        byte[] compressed = Arrays.copyOfRange(uncompressedPub, 0, 33);
        compressed[0] = (byte) (0x02 | (uncompressedPub[64] & 0x1));
        return compressed;
    }

    private String cardBip32GetXpub(String stringPath, ExtendedKey.Header xtype) throws IOException, APDUException {
        KeyPath keyPath = new KeyPath(stringPath);
        int bytepathLen = keyPath.getData().length;
        int depth = bytepathLen / 4;
        APDUResponse rapdu = this.cardProtocol.exportKey(keyPath.getData(), keyPath.getSource(), false, KeycardCommandSet.EXPORT_KEY_P2_EXTENDED_PUBLIC).checkOK();
        BIP32KeyPair extendedkey = BIP32KeyPair.fromTLV(rapdu.getData());

        byte[] fingerprint = new byte[4];
        byte[] childNumber = new byte[4];

        if(depth == 0) { //masterkey
            // fingerprint and childnumber set to all-zero bytes by default
            //fingerprint= bytes([0,0,0,0])
            //childNumber= bytes([0,0,0,0])
        } else { //get parent info
            APDUResponse rapdu2 = this.cardProtocol.exportKey(Arrays.copyOfRange(keyPath.getData(), 0, bytepathLen - 4), keyPath.getSource(), false, KeycardCommandSet.EXPORT_KEY_P2_PUBLIC_ONLY).checkOK();
            BIP32KeyPair keyParent = BIP32KeyPair.fromTLV(rapdu2.getData());
            byte[] identifier = Utils.sha256hash160(compressedPub(keyParent.getPublicKey()));
            fingerprint = Arrays.copyOfRange(identifier, 0, 4);
            childNumber = Arrays.copyOfRange(keyPath.getData(), bytepathLen - 4, bytepathLen);
        }

        ByteBuffer buffer = ByteBuffer.allocate(78);
        buffer.putInt(xtype.getHeader());
        buffer.put((byte) depth);
        buffer.put(fingerprint);
        buffer.put(childNumber);
        buffer.put(extendedkey.getChainCode()); // chaincode
        buffer.put(compressedPub(extendedkey.getPublicKey())); // pubkey (compressed)
        byte[] xpubByte = buffer.array();

        return Base58.encodeChecked(xpubByte);
    }

    /*
     * Keycard derives BIP32 keys based on the fullPath (from masterseed to leaf), not the partial path from a given xpub.
     * the basePath (from masterseed to xpub) is only provided in Keycard.java:getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty)
     * In Keycard:getKeystore(), no derivation path (i.e. basePath from masterSeed to xpub or relative path) is given and no derivation is reliably available as a object field.
     * currently, we try to get the path from this.basePath if available (or use a default value) but it's not reliable enough
     */
    @Override
    public Keystore getKeystore() throws CardException {
        String keyDerivationString = (this.basePath != null ? this.basePath : getDefaultScriptType().getDefaultDerivationPath());
        ExtendedKey.Header xtype = Network.get().getXpubHeader();

        String xpub;
        String masterXpub;
        try {
            this.authenticate();
            xpub = this.cardBip32GetXpub(keyDerivationString, xtype);
            masterXpub = this.cardBip32GetXpub("m", xtype);
        } catch(IOException | APDUException e) {
            throw new CardException(e);
        }

        ExtendedKey extendedKey = ExtendedKey.fromDescriptor(xpub);
        ExtendedKey masterExtendedKey = ExtendedKey.fromDescriptor(masterXpub);
        String masterFingerprint = Utils.bytesToHex(masterExtendedKey.getKey().getFingerprint());
        KeyDerivation keyDerivation = new KeyDerivation(masterFingerprint, keyDerivationString, true);

        Keystore keystore = new Keystore();
        keystore.setLabel(WalletModel.KEYCARD.toDisplayString());
        keystore.setKeyDerivation(keyDerivation);
        keystore.setSource(KeystoreSource.HW_USB);
        keystore.setExtendedPublicKey(extendedKey);
        keystore.setWalletModel(WalletModel.KEYCARD);

        return keystore;
    }

    @Override
    public Service<PSBT> getSignService(Wallet wallet, PSBT psbt, StringProperty messageProperty) {
        return new KeycardApi.SignService(wallet, psbt, messageProperty);
    }

    void sign(Wallet wallet, PSBT psbt) throws CardException {
        Map<PSBTInput, WalletNode> signingNodes = wallet.getSigningNodes(psbt);
        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            if(!psbtInput.isSigned()) {
                WalletNode signingNode = signingNodes.get(psbtInput);
                List<Keystore> keystores = wallet.getKeystores();
                // recover derivation path from Keycard keystore
                String fullPath = null;
                for(int i = 0; i < keystores.size(); i++) {
                    Keystore keystore = keystores.get(i);
                    WalletModel walletModel = keystore.getWalletModel();
                    if(walletModel == WalletModel.KEYCARD) {
                        String basePath = keystore.getKeyDerivation().getDerivationPath();
                        String extendedPath = signingNode.getDerivationPath().substring(1);
                        fullPath = basePath + extendedPath;
                        break;
                    }
                }
                if(fullPath == null) {
                    // recover a default derivation path from first keystore
                    Keystore keystore = keystores.get(0);
                    String basePath = keystore.getKeyDerivation().getDerivationPath();
                    String extendedPath = signingNode.getDerivationPath().substring(1);
                    fullPath = basePath + extendedPath;
                }
                psbtInput.sign(new KeycardApi.CardPSBTInputSigner(signingNode, fullPath));
            }
        }
    }

    @Override
    public Service<String> getSignMessageService(String message, ScriptType scriptType, List<ChildNumber> derivation, StringProperty messageProperty) {
        return new KeycardApi.SignMessageService(message, scriptType, derivation, messageProperty);
    }

    String signMessage(String message, ScriptType scriptType, List<ChildNumber> derivation) throws CardException {
        String fullpath = KeyDerivation.writePath(derivation);
        ECKey pubkey;

        try {
            authenticate();
            APDUResponse rapdu = cardProtocol.exportKey(fullpath, false, KeycardCommandSet.EXPORT_KEY_P2_PUBLIC_ONLY).checkOK();
            BIP32KeyPair keys = BIP32KeyPair.fromTLV(rapdu.getData());
            pubkey = ECKey.fromPublicOnly(compressedPub(keys.getPublicKey()));
        } catch(IOException | APDUException e) {
            throw new CardException(e);
        }

        // sign msg
        return pubkey.signMessage(message, scriptType, hash -> {
            try {
                // do the signature with Keycard
                APDUResponse rapdu2 = cardProtocol.signWithPath(hash.getBytes(), fullpath, false).checkOK();
                RecoverableSignature sig = new RecoverableSignature(hash.getBytes(), rapdu2.getData());
                return new ECDSASignature(new BigInteger(1, sig.getR()), new BigInteger(1, sig.getS()));
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Service<ECKey> getPrivateKeyService(Integer slot, StringProperty messageProperty) {
        throw new UnsupportedOperationException("Keycard does not support private key export");
    }

    @Override
    public Service<Address> getAddressService(StringProperty messageProperty) {
        return null;
    }

    @Override
    public void disconnect() {
        try {
            cardTransport.disconnect();
        } catch(CardException e) {
            log.error("Error disconnecting Keycard" + e);
        }
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
                        throw new CardException("Failed to initialize Keycard - no seed provided");
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
                // verify PIN
                authenticate();

                if(signatureType == TransactionSignature.Type.ECDSA) {
                    // do the signature with Keycard
                    APDUResponse rapdu = cardProtocol.signWithPath(hash.getBytes(), fullPath, false).checkOK();
                    RecoverableSignature sig = new RecoverableSignature(hash.getBytes(), rapdu.getData());
                    pubkey = ECKey.fromPublicOnly(compressedPub(sig.getPublicKey()));

                    ECDSASignature ecdsaSig = new ECDSASignature(new BigInteger(1, sig.getR()), new BigInteger(1, sig.getS())).toCanonicalised();
                    TransactionSignature txSig = new TransactionSignature(ecdsaSig, sigHash);

                    boolean isCorrect = pubkey.verify(hash, txSig);
                    return txSig;
                } else {
                    throw new CardException(WalletModel.KEYCARD.toDisplayString() + " cannot sign Taproot transactions");
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
