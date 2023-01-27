package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Base58;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.SigHash;
import com.sparrowwallet.drongo.protocol.TransactionSignature;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.psbt.PSBTInputSigner;
import com.sparrowwallet.drongo.wallet.*;
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

public class CardApi {
    private static final Logger log = LoggerFactory.getLogger(CardApi.class);

    private final WalletModel cardType;
    private final CardProtocol cardProtocol;
    private String cvc;

    public CardApi(String cvc) throws CardException {
        this(WalletModel.TAPSIGNER, cvc);
    }

    public CardApi(WalletModel cardType, String cvc) throws CardException {
        this.cardType = cardType;
        this.cardProtocol = new CardProtocol();
        this.cvc = cvc;
    }

    public boolean isInitialized() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.isInitialized();
    }

    public void initialize(byte[] chainCode) throws CardException {
        cardProtocol.verify();
        cardProtocol.setup(cvc, chainCode);
    }

    public WalletModel getCardType() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.getCardType();
    }

    CardStatus getStatus() throws CardException {
        CardStatus cardStatus = cardProtocol.getStatus();
        if(cardStatus.getCardType() != cardType) {
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

    public Service<Void> getAuthDelayService() throws CardException {
        CardStatus cardStatus = getStatus();
        if(cardStatus.auth_delay != null) {
            return new AuthDelayService(cardStatus);
        }

        return null;
    }

    public boolean requiresBackup() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.requiresBackup();
    }

    public Service<String> getBackupService() {
        return new BackupService();
    }

    String getBackup() throws CardException {
        CardBackup cardBackup = cardProtocol.backup(cvc);
        return Utils.bytesToHex(cardBackup.data);
    }

    public boolean changePin(String newCvc) throws CardException {
        CardChange cardChange = cardProtocol.change(cvc, newCvc);
        if(cardChange.success) {
            cvc = newCvc;
        }

        return cardChange.success;
    }

    public void setDerivation(List<ChildNumber> derivation) throws CardException {
        cardProtocol.derive(cvc, derivation);
    }

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

    public void disconnect() {
        try {
            cardProtocol.disconnect();
        } catch(CardException e) {
            log.warn("Error disconnecting from card reader", e);
        }
    }

    public static boolean isReaderAvailable() {
        return CardTransport.isReaderAvailable();
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
                throw new IllegalStateException(cardType.toDisplayString() + " cannot sign " + signatureType + " transactions.");
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
}
