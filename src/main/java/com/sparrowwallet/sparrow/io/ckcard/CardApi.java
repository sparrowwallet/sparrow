package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.Base58;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.WalletModel;
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

public class CardApi {
    private static final Logger log = LoggerFactory.getLogger(CardApi.class);

    private final CardProtocol cardProtocol;
    private String cvc;

    public CardApi(String cvc) throws CardException {
        this.cardProtocol = new CardProtocol();
        this.cvc = cvc;
    }

    public void initialize() throws CardException {
        cardProtocol.verify();
        cardProtocol.setup(cvc, null);
    }

    CardStatus getStatus() throws CardException {
        return cardProtocol.getStatus();
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
        CardStatus cardStatus = cardProtocol.getStatus();

        CardXpub masterXpub = cardProtocol.xpub(cvc, true);
        ExtendedKey masterXpubkey = ExtendedKey.fromDescriptor(Base58.encodeChecked(masterXpub.xpub));
        String masterFingerprint = Utils.bytesToHex(masterXpubkey.getKey().getFingerprint());

        KeyDerivation keyDerivation = new KeyDerivation(masterFingerprint, cardStatus.getDerivation());

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
}
