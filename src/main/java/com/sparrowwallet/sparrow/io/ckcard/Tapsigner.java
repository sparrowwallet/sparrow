package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.KeystoreCardImport;
import com.sparrowwallet.sparrow.io.ImportException;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.StringProperty;

import javax.smartcardio.CardException;
import java.util.List;

public class Tapsigner implements KeystoreCardImport {
    @Override
    public boolean isInitialized() throws CardException {
        CkCardApi cardApi = null;
        try {
            cardApi = new CkCardApi(null);
            return cardApi.isInitialized();
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public void initialize(String pin, byte[] chainCode, StringProperty messageProperty) throws CardException {
        if(pin.length() < 6) {
            throw new CardException("PIN too short.");
        }

        if(pin.length() > 32) {
            throw new CardException("PIN too long.");
        }

        CkCardApi cardApi = null;
        try {
            cardApi = new CkCardApi(pin);
            CardStatus cardStatus = cardApi.getStatus();
            if(cardStatus.isInitialized()) {
                throw new IllegalStateException("Card is already initialized.");
            }
            cardApi.checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);
            cardApi.initialize(0, chainCode);
        } finally {
            if(cardApi != null) {
                cardApi.disconnect();
            }
        }
    }

    @Override
    public Keystore getKeystore(String pin, List<ChildNumber> derivation, StringProperty messageProperty) throws ImportException {
        if(pin.length() < 6) {
            throw new ImportException("PIN too short.");
        }

        if(pin.length() > 32) {
            throw new ImportException("PIN too long.");
        }

        CkCardApi cardApi = null;
        try {
            cardApi = new CkCardApi(pin);
            CardStatus cardStatus = cardApi.getStatus();
            if(!cardStatus.isInitialized()) {
                throw new IllegalStateException("Card is not initialized.");
            }
            cardApi.checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

            if(!derivation.equals(cardStatus.getDerivation())) {
                cardApi.setDerivation(derivation);
            }
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
        return "Import the keystore from your Tapsigner by placing it on the card reader.";
    }

    @Override
    public String getName() {
        return "Tapsigner";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.TAPSIGNER;
    }
}
