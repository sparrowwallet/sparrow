package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.KeystoreCardImport;
import com.sparrowwallet.sparrow.io.ImportException;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javax.smartcardio.CardException;
import java.util.List;

public class CkCard implements KeystoreCardImport {
    private final StringProperty messageProperty = new SimpleStringProperty("");

    @Override
    public Keystore getKeystore(String pin, List<ChildNumber> derivation) throws ImportException {
        if(pin.length() < 6) {
            throw new ImportException("PIN too short");
        }

        if(pin.length() > 32) {
            throw new ImportException("PIN too long");
        }

        CardApi cardApi = null;
        try {
            cardApi = new CardApi(pin);
            CardStatus cardStatus = cardApi.getStatus();
            if(!cardStatus.isInitialized()) {
                cardApi.initialize();
                cardStatus = cardApi.getStatus();
            }
            cardApi.checkWait(cardStatus, new SimpleIntegerProperty(), messageProperty);

            if(!derivation.equals(cardStatus.getDerivation())) {
                cardApi.setDerivation(derivation);
            }
            return cardApi.getKeystore();
        } catch(CardException e) {
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

    public StringProperty messageProperty() {
        return messageProperty;
    }
}
