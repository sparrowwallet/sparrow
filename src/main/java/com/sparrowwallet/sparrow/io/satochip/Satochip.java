package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.KeystoreCardImport;
import com.sparrowwallet.sparrow.io.ImportException;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import java.util.List;

public class Satochip implements KeystoreCardImport {
    private static final Logger log = LoggerFactory.getLogger(Satochip.class);

    @Override
    public boolean isInitialized() throws CardException {
        log.debug("SATOCHIP Satochip isInitialized()");
        SatoCardApi cardApi = null;
        try {
            cardApi = new SatoCardApi(WalletModel.SATOCHIP, null);
            return cardApi.isInitialized();
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public void initialize(String pin, byte[] chainCode, StringProperty messageProperty) throws CardException {
        log.debug("SATOCHIP Satochip initialize()");
        if(pin.length() < 4) {
            throw new CardException("PIN too short.");
        }

        if(pin.length() > 16) {
            throw new CardException("PIN too long.");
        }

        SatoCardApi cardApi = null;
        try {
            cardApi = new SatoCardApi(WalletModel.SATOCHIP, pin);
            SatoCardStatus cardStatus = cardApi.getStatus();
            if(cardStatus.isInitialized()) {
                throw new IllegalStateException("Card is already initialized.");
            }
            // TODO!
            // not used currently
            // initialization is done through SatoCardApi.initialize()
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public Keystore getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty) throws ImportException {
        log.debug("SATOCHIP Satochip getKeystore() derivation:" + derivation);
        if(pin.length() < 4) {
            throw new ImportException("PIN too short.");
        }

        if(pin.length() > 16) {
            throw new ImportException("PIN too long.");
        }

        SatoCardApi cardApi = null;
        try {
            cardApi = new SatoCardApi(WalletModel.SATOCHIP, pin);
            SatoCardStatus cardStatus = cardApi.getStatus();
            if(!cardStatus.isInitialized()) {
                throw new IllegalStateException("Card is not initialized.");
            }
            cardApi.setDerivation(derivation);
            return cardApi.getKeystore();
        } catch(Exception e) {
            e.printStackTrace();
            log.error("SATOCHIP Satochip getKeystore() Exception: " + e);
            throw new ImportException(e);
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        log.debug("SATOCHIP Satochip getKeystoreImportDescription()");
        return "Import the keystore from your Satochip by inserting it in the card reader.";
    }

    @Override
    public String getName() {
        log.debug("SATOCHIP Satochip getName()");
        return "Satochip";
    }

    @Override
    public WalletModel getWalletModel() {
        log.debug("SATOCHIP Satochip getWalletModel()");
        return WalletModel.SATOCHIP;
    }
}
