package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
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
import java.util.*;

public class CkCardApi extends CardApi {
    private static final Logger log = LoggerFactory.getLogger(CkCardApi.class);

    private final WalletModel cardType;
    private final CardProtocol cardProtocol;
    private String cvc;

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
    public void initialize(int slot, byte[] chainCode) throws CardException {
        cardProtocol.verify();
        cardProtocol.setup(cvc, slot, chainCode);
    }

    @Override
    public WalletModel getCardType() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.getCardType();
    }

    @Override
    public int getCurrentSlot() throws CardException {
        CardStatus cardStatus = getStatus();
        return cardStatus.getCurrentSlot();
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
        return Base64.getEncoder().encodeToString(cardBackup.data);
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
    public Service<Void> getInitializationService(byte[] entropy, StringProperty messageProperty) {
        if(cardType == WalletModel.TAPSIGNER) {
            return new CardImportPane.CardInitializationService(new Tapsigner(), cvc, entropy, messageProperty);
        } else if(cardType == WalletModel.SATSCHIP) {
            return new CardImportPane.CardInitializationService(new Satschip(), cvc, entropy, messageProperty);
        }

        return new CardInitializationService(entropy, messageProperty);
    }

    @Override
    public Service<Keystore> getImportService(List<ChildNumber> derivation, StringProperty messageProperty) {
        if(cardType == WalletModel.SATSCHIP) {
            return new CardImportPane.CardImportService(new Satschip(), cvc, derivation, messageProperty);
        }

        return new CardImportPane.CardImportService(new Tapsigner(), cvc, derivation, messageProperty);
    }

    @Override
    public Keystore getKeystore() throws CardException {
        KeyDerivation keyDerivation = getKeyDerivation();

        CardXpub derivedXpub = cardProtocol.xpub(cvc, false);
        ExtendedKey derivedXpubkey = ExtendedKey.fromDescriptor(Base58.encodeChecked(derivedXpub.xpub));

        Keystore keystore = new Keystore();
        keystore.setLabel(cardType.toDisplayString());
        keystore.setKeyDerivation(keyDerivation);
        keystore.setSource(KeystoreSource.HW_AIRGAPPED);
        keystore.setExtendedPublicKey(derivedXpubkey);
        keystore.setWalletModel(cardType);

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
        List<ChildNumber> keystoreDerivation;
        List<ChildNumber> subPathDerivation;

        Optional<ChildNumber> firstUnhardened = derivation.stream().filter(cn -> !cn.isHardened()).findFirst();
        if(firstUnhardened.isPresent()) {
            int index = derivation.indexOf(firstUnhardened.get());
            keystoreDerivation = derivation.subList(0, index);
            subPathDerivation = derivation.subList(index, derivation.size());
        } else {
            keystoreDerivation = derivation;
            subPathDerivation = Collections.emptyList();
        }

        Keystore cardKeystore = getKeystore();
        KeyDerivation cardKeyDerivation = cardKeystore.getKeyDerivation();
        Keystore signingKeystore = cardKeystore;
        try {
            if(!cardKeyDerivation.getDerivation().equals(keystoreDerivation)) {
                setDerivation(keystoreDerivation);
                signingKeystore = getKeystore();
            }

            ECKey signingPubKey;
            if(subPathDerivation.isEmpty()) {
                signingPubKey = signingKeystore.getExtendedPublicKey().getKey();
            } else {
                WalletNode addressNode = new WalletNode(KeyDerivation.writePath(subPathDerivation));
                signingPubKey = signingKeystore.getPubKey(addressNode);
            }

            return signingPubKey.signMessage(message, scriptType, hash -> {
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
    public Service<ECKey> getPrivateKeyService(Integer slot, StringProperty messageProperty) {
        return new PrivateKeyService(slot, messageProperty);
    }

    ECKey getPrivateKey(int slot, int currentSlot) throws CardException {
        if(slot != currentSlot) {
            CardAuthDump cardAuthDump = cardProtocol.dump(cvc, slot);
            return cardAuthDump.getPrivateKey();
        }

        CardUnseal cardUnseal = cardProtocol.unseal(cvc, slot);
        return cardUnseal.getPrivateKey();
    }

    @Override
    public Service<Address> getAddressService(StringProperty messageProperty) {
        return new AddressService(messageProperty);
    }

    Address getAddress(int currentSlot, int lastSlot, String addr) throws CardException {
        if(currentSlot == lastSlot) {
            CardDump cardDump = cardProtocol.dump(currentSlot);
            if(!cardDump.sealed) {
                return cardDump.getAddress();
            }
        }

        CardRead cardRead = cardProtocol.read(null, currentSlot);
        Address address = getDefaultScriptType().getAddress(cardRead.getPubKey());

        String left = addr.substring(0, addr.indexOf('_'));
        String right = addr.substring(addr.lastIndexOf('_') + 1);

        if(!address.toString().startsWith(left) || !address.toString().endsWith(right)) {
            throw new CardException("Card authentication failed: Provided pubkey does not match given address");
        }

        return address;
    }

    @Override
    public void disconnect() {
        try {
            cardProtocol.disconnect();
        } catch(CardException e) {
            log.warn("Error disconnecting from card reader", e);
        }
    }

    public class CardInitializationService extends Service<Void> {
        private final byte[] chainCode;
        private final StringProperty messageProperty;

        public CardInitializationService(byte[] chainCode, StringProperty messageProperty) {
            this.chainCode = chainCode;
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() throws Exception {
                    CardStatus cardStatus = getStatus();
                    if(cardStatus.getCardType() != WalletModel.SATSCARD) {
                        throw new IllegalStateException("Please use a " + WalletModel.SATSCARD.toDisplayString() + ".");
                    }
                    if(cardStatus.isInitialized()) {
                        throw new IllegalStateException("Card already initialized.");
                    }

                    checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

                    initialize(cardStatus.getCurrentSlot(), chainCode);
                    return null;
                }
            };
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
                    if(cardStatus.getCardType() != WalletModel.TAPSIGNER && cardStatus.getCardType() != WalletModel.SATSCHIP) {
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
                    if(cardStatus.getCardType() != WalletModel.TAPSIGNER && cardStatus.getCardType() != WalletModel.SATSCHIP) {
                        throw new IllegalStateException("Please use a " + WalletModel.TAPSIGNER.toDisplayString() + " to sign messages.");
                    }

                    checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

                    return signMessage(message, scriptType, derivation);
                }
            };
        }
    }

    public class PrivateKeyService extends Service<ECKey> {
        private Integer slot;
        private final StringProperty messageProperty;

        public PrivateKeyService(Integer slot, StringProperty messageProperty) {
            this.slot = slot;
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<ECKey> createTask() {
            return new Task<>() {
                @Override
                protected ECKey call() throws Exception {
                    CardStatus cardStatus = getStatus();
                    if(cardStatus.getCardType() != WalletModel.SATSCARD) {
                        throw new IllegalStateException("Please use a " + WalletModel.SATSCARD.toDisplayString() + " to retrieve a private key.");
                    }

                    int currentSlot = cardStatus.getCurrentSlot();
                    if(slot == null) {
                        slot = currentSlot;
                    }

                    if(slot == currentSlot && !cardStatus.isInitialized()) {
                        //If card has been unsealed, but a new slot is not initialized, retrieve private key for previous slot
                        slot = slot - 1;
                    }

                    checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

                    return getPrivateKey(slot, currentSlot);
                }
            };
        }
    }

    public class AddressService extends Service<Address> {
        private final StringProperty messageProperty;

        public AddressService(StringProperty messageProperty) {
            this.messageProperty = messageProperty;
        }

        @Override
        protected Task<Address> createTask() {
            return new Task<>() {
                @Override
                protected Address call() throws Exception {
                    CardStatus cardStatus = getStatus();
                    if(cardStatus.getCardType() != WalletModel.SATSCARD) {
                        throw new IllegalStateException("Please use a " + WalletModel.SATSCARD.toDisplayString() + " to retrieve an address.");
                    }
                    if(!cardStatus.isInitialized()) {
                        throw new IllegalStateException("Please re-initialize card before attempting to get the address.");
                    }

                    checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

                    return getAddress(cardStatus.getCurrentSlot(), cardStatus.getLastSlot(), cardStatus.addr);
                }
            };
        }
    }
}
