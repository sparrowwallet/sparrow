package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECKey;
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

import javax.smartcardio.CardException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CkCardApi extends CardApi {
    private static final Logger log = LoggerFactory.getLogger(CkCardApi.class);

    private final WalletModel cardType;
    private final CardProtocol cardProtocol;
    private String cvc;

    public CkCardApi(String cvc) throws CardException {
        this(WalletModel.TAPSIGNER, cvc);
    }

    public CkCardApi(WalletModel cardType, String cvc) throws CardException {
        this.cardType = cardType;
        this.cardProtocol = new CardProtocol();
        this.cvc = cvc;
    }

    @Override
    public boolean isInitialized() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.isInitialized();
    }

    @Override
    public void initialize(byte[] chainCode) throws CardException {
        cardProtocol.verify();
        cardProtocol.setup(cvc, chainCode);
    }

    @Override
    public WalletModel getCardType() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.getCardType();
    }

    @Override
    public ScriptType getDefaultScriptType() {
        return ScriptType.P2WPKH;
    }

    CardStatus getStatus() throws CardException {
        CardStatus cardStatus = cardProtocol.getStatus();
        if(cardType != null && cardStatus.getCardType() != cardType) {
            throw new CardException("Please use a " + cardType.toDisplayString() + " card.");
        }
        return cardStatus;
    }

    void checkWait(CardStatus cardStatus, IntegerProperty delayProperty, StringProperty messageProperty) throws CardException {
        if(cardStatus.auth_delay != null) {
            int delay = cardStatus.auth_delay.intValue();
            while(delay > 0) {
                delayProperty.set(delay);
                messageProperty.set("Auth delay, waiting " + delay + "s...");
                CardWait cardWait = cardProtocol.authWait();
                if(cardWait.success) {
                    delay = cardWait.auth_delay == null ? 0 : cardWait.auth_delay.intValue();
                }
            }
        }
    }

    @Override
    public Service<Void> getAuthDelayService() throws CardException {
        CardStatus cardStatus = getStatus();
        if(cardStatus.auth_delay != null) {
            return new AuthDelayService(cardStatus);
        }

        return null;
    }

    @Override
    public boolean requiresBackup() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.requiresBackup();
    }

    @Override
    public Service<String> getBackupService() {
        return new BackupService();
    }

    String getBackup() throws CardException {
        CardBackup cardBackup = cardProtocol.backup(cvc);
        return Utils.bytesToHex(cardBackup.data);
    }

    @Override
    public boolean changePin(String newCvc) throws CardException {
        CardChange cardChange = cardProtocol.change(cvc, newCvc);
        if(cardChange.success) {
            cvc = newCvc;
        }

        return cardChange.success;
    }

    void setDerivation(List<ChildNumber> derivation) throws CardException {
        cardProtocol.derive(cvc, derivation);
    }

    @Override
    public Service<Void> getInitializationService(byte[] entropy) {
        return new CardImportPane.CardInitializationService(new Tapsigner(), entropy);
    }

    @Override
    public Service<Keystore> getImportService(List<ChildNumber> derivation, StringProperty messageProperty) {
        return new CardImportPane.CardImportService(new Tapsigner(), cvc, derivation, messageProperty);
    }

    @Override
    public Keystore getKeystore() throws CardException {
        KeyDerivation keyDerivation = getKeyDerivation();

        CardXpub derivedXpub = cardProtocol.xpub(cvc, false);
        ExtendedKey derivedXpubkey = ExtendedKey.fromDescriptor(Base58.encodeChecked(derivedXpub.xpub));

        Keystore keystore = new Keystore();
        keystore.setLabel(WalletModel.TAPSIGNER.toDisplayString());
        keystore.setKeyDerivation(keyDerivation);
        keystore.setSource(KeystoreSource.HW_AIRGAPPED);
        keystore.setExtendedPublicKey(derivedXpubkey);
        keystore.setWalletModel(WalletModel.TAPSIGNER);

        return keystore;
    }

    private KeyDerivation getKeyDerivation() throws CardException {
        String masterFingerprint = getMasterFingerprint();
        return new KeyDerivation(masterFingerprint, getStatus().getDerivation());
    }

    private String getMasterFingerprint() throws CardException {
        CardXpub masterXpub = cardProtocol.xpub(cvc, true);
        ExtendedKey masterXpubkey = ExtendedKey.fromDescriptor(Base58.encodeChecked(masterXpub.xpub));
        return Utils.bytesToHex(masterXpubkey.getKey().getFingerprint());
    }

    @Override
    public Service<PSBT> getSignService(Wallet wallet, PSBT psbt, StringProperty messageProperty) {
        return new SignService(wallet, psbt, messageProperty);
    }

    void sign(Wallet wallet, PSBT psbt) throws CardException {
        Keystore cardKeystore = getKeystore();
        KeyDerivation cardKeyDerivation = cardKeystore.getKeyDerivation();

        Map<PSBTInput, WalletNode> signingNodes = wallet.getSigningNodes(psbt);
        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            if(!psbtInput.isSigned()) {
                WalletNode signingNode = signingNodes.get(psbtInput);
                KeyDerivation changedDerivation = null;
                try {
                    ECKey cardSigningPubKey = cardKeystore.getPubKey(signingNode);
                    if(wallet.getKeystores().stream().noneMatch(keystore -> keystore.getPubKey(signingNode).equals(cardSigningPubKey))) {
                        Optional<KeyDerivation> optKeyDerivation = wallet.getKeystores().stream().map(Keystore::getKeyDerivation)
                                .filter(kd -> kd.getMasterFingerprint().equals(cardKeyDerivation.getMasterFingerprint()) && !kd.getDerivation().equals(cardKeyDerivation.getDerivation())).findFirst();
                        if(optKeyDerivation.isPresent()) {
                            changedDerivation = optKeyDerivation.get();
                            setDerivation(changedDerivation.getDerivation());

                            Keystore changedKeystore = getKeystore();
                            ECKey changedSigningPubKey = changedKeystore.getPubKey(signingNode);
                            if(wallet.getKeystores().stream().noneMatch(keystore -> keystore.getPubKey(signingNode).equals(changedSigningPubKey))) {
                                throw new CardException("Card cannot recognise public key for signing address.");
                            }
                        } else {
                            throw new CardException("Card cannot recognise public key for signing address.");
                        }
                    }

                    psbtInput.sign(new CardPSBTInputSigner(signingNode));
                } finally {
                    if(changedDerivation != null) {
                        setDerivation(cardKeyDerivation.getDerivation());
                    }
                }
            }
        }
    }

    @Override
    public Service<String> getSignMessageService(String message, ScriptType scriptType, List<ChildNumber> derivation, StringProperty messageProperty) {
        return new SignMessageService(message, scriptType, derivation, messageProperty);
    }

    String signMessage(String message, ScriptType scriptType, List<ChildNumber> derivation) throws CardException {
        List<ChildNumber> keystoreDerivation = derivation.subList(0, derivation.size() - 2);
        List<ChildNumber> subPathDerivation = derivation.subList(derivation.size() - 2, derivation.size());

        Keystore cardKeystore = getKeystore();
        KeyDerivation cardKeyDerivation = cardKeystore.getKeyDerivation();
        Keystore signingKeystore = cardKeystore;
        try {
            if(!cardKeyDerivation.getDerivation().equals(keystoreDerivation)) {
                setDerivation(keystoreDerivation);
                signingKeystore = getKeystore();
            }

            WalletNode addressNode = new WalletNode(KeyDerivation.writePath(subPathDerivation));
            ECKey addressPubKey = signingKeystore.getPubKey(addressNode);
            return addressPubKey.signMessage(message, scriptType, hash -> {
                try {
                    CardSign cardSign = cardProtocol.sign(cvc, subPathDerivation, hash);
                    return cardSign.getSignature();
                } catch(CardException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            if(signingKeystore != cardKeystore) {
                setDerivation(cardKeystore.getKeyDerivation().getDerivation());
            }
        }
    }

    @Override
    public Service<ECKey> getUnsealService(StringProperty messageProperty) {
        return new UnsealService(messageProperty);
    }

    ECKey unseal() throws CardException {
        CardUnseal cardUnseal = cardProtocol.unseal(cvc);
        return cardUnseal.getPrivateKey();
    }

    @Override
    public void disconnect() {
        try {
            cardProtocol.disconnect();
        } catch(CardException e) {
            log.warn("Error disconnecting from card reader", e);
        }
    }

    public class AuthDelayService extends Service<Void> {
        private final CardStatus cardStatus;
        private final IntegerProperty delayProperty;
        private final StringProperty messageProperty;

        AuthDelayService(CardStatus cardStatus) {
            this.cardStatus = cardStatus;
            this.delayProperty = new SimpleIntegerProperty();
            this.messageProperty = new SimpleStringProperty();
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() throws Exception {
                    delayProperty.addListener((observable, oldValue, newValue) -> updateProgress(cardStatus.auth_delay.intValue() - newValue.intValue(), cardStatus.auth_delay.intValue()));
                    messageProperty.addListener((observable, oldValue, newValue) -> updateMessage(newValue));
                    checkWait(cardStatus, delayProperty, messageProperty);
                    return null;
                }
            };
        }
    }

    public class BackupService extends Service<String> {
        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                @Override
                protected String call() throws Exception {
                    return getBackup();
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
                    CardStatus cardStatus = getStatus();
                    if(cardStatus.getCardType() != WalletModel.TAPSIGNER) {
                        throw new IllegalStateException("Please use a " + WalletModel.TAPSIGNER.toDisplayString() + " to sign transactions.");
                    }

                    checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

                    sign(wallet, psbt);
                    return psbt;
                }
            };
        }
    }

    private class CardPSBTInputSigner implements PSBTInputSigner {
        private final WalletNode signingNode;
        private ECKey pubkey;

        public CardPSBTInputSigner(WalletNode signingNode) {
            this.signingNode = signingNode;
        }

        @Override
        public TransactionSignature sign(Sha256Hash hash, SigHash sigHash, TransactionSignature.Type signatureType) {
            if(signatureType != TransactionSignature.Type.ECDSA) {
                throw new IllegalStateException(WalletModel.TAPSIGNER.toDisplayString() + " cannot sign " + signatureType + " transactions.");
            }

            try {
                CardSign cardSign = cardProtocol.sign(cvc, signingNode.getDerivation(), hash);
                pubkey = cardSign.getPubKey();
                return new TransactionSignature(cardSign.getSignature(), sigHash);
            } catch(CardException e) {
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
                    CardStatus cardStatus = getStatus();
                    if(cardStatus.getCardType() != WalletModel.TAPSIGNER) {
                        throw new IllegalStateException("Please use a " + WalletModel.TAPSIGNER.toDisplayString() + " to sign messages.");
                    }

                    checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

                    return signMessage(message, scriptType, derivation);
                }
            };
        }
    }

    public class UnsealService extends Service<ECKey> {
        private final StringProperty messageProperty;

        public UnsealService(StringProperty messageProperty) {
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<ECKey> createTask() {
            return new Task<>() {
                @Override
                protected ECKey call() throws Exception {
                    CardStatus cardStatus = getStatus();
                    if(cardStatus.getCardType() != WalletModel.SATSCARD) {
                        throw new IllegalStateException("Please use a " + WalletModel.SATSCARD.toDisplayString() + " to unseal private keys.");
                    }

                    checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

                    return unseal();
                }
            };
        }
    }
}
