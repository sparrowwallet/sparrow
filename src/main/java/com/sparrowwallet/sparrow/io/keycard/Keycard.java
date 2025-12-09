package com.sparrowwallet.sparrow.io.keycard;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.io.KeystoreCardImport;
import javafx.beans.property.StringProperty;
import org.apache.commons.lang3.StringUtils;

import javax.smartcardio.CardException;
import java.util.List;

public class Keycard implements KeystoreCardImport {

    @Override
    public boolean isInitialized() throws CardException {
        KeycardApi cardApi = null;
        try {
            cardApi = new KeycardApi(WalletModel.KEYCARD, null);
            return cardApi.isInitialized();
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public void initialize(String pin, byte[] entropy, StringProperty messageProperty) throws CardException {
        if(!StringUtils.isNumeric(pin)) {
            throw new CardException("PIN must be all digits.");
        }

        if(pin.length() != 6) {
            throw new CardException("PIN must be 6 digit longs.");
        }

        KeycardApi cardApi = null;
        try {
            cardApi = new KeycardApi(WalletModel.KEYCARD, pin);
            if(cardApi.isInitialized()) {
                throw new IllegalStateException("Card is already initialized.");
            }

            cardApi.initialize(0, entropy);
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public Keystore getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty) throws ImportException {
        if(!StringUtils.isNumeric(pin)) {
            throw new ImportException("PIN must be all digits.");
        }

        if(pin.length() != 6) {
            throw new ImportException("PIN must be 6 digit longs.");
        }

        KeycardApi cardApi = null;
        try {
            cardApi = new KeycardApi(WalletModel.KEYCARD, pin);
            if(!cardApi.isInitialized()) {
                throw new IllegalStateException("Card is not initialized.");
            }
            cardApi.setDerivation(derivation);
            return cardApi.getKeystore();
        } catch(Exception e) {
            throw new ImportException(e);
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import the keystore from your Keycard by inserting or placing it on the card reader.";
    }

    @Override
    public String getName() {
        return "Keycard";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.KEYCARD;
    }
}
