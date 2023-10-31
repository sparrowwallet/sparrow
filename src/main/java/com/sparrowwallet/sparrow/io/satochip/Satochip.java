package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.KeystoreCardImport;
import com.sparrowwallet.sparrow.io.ImportException;
import javafx.beans.property.StringProperty;

import javax.smartcardio.CardException;
import java.util.List;

public class Satochip implements KeystoreCardImport {
    @Override
    public boolean isInitialized() throws CardException {
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
    public void initialize(String pin, byte[] entropy, StringProperty messageProperty) throws CardException {
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

            cardApi.initialize(0, entropy);
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public Keystore getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty) throws ImportException {
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
            throw new ImportException(e);
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public String getKeystoreImportDescription(int account) {
        return "Import the keystore from your Satochip by inserting or placing it on the card reader.";
    }

    @Override
    public String getName() {
        return "Satochip";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.SATOCHIP;
    }
}
