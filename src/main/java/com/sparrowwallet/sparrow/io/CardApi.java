package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.ckcard.CkCardApi;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import javax.smartcardio.TerminalFactory;
import java.util.Collections;
import java.util.List;

public abstract class CardApi {
    private static final Logger log = LoggerFactory.getLogger(CardApi.class);

    public static List<WalletModel> getConnectedCards() throws CardException {
        try {
            CkCardApi ckCardApi = new CkCardApi(null, null);
            return List.of(ckCardApi.getCardType());
        } catch(Exception e) {
            //ignore
        }

        return Collections.emptyList();
    }

    public static CardApi getCardApi(WalletModel walletModel, String pin) throws CardException {
        if(walletModel == WalletModel.TAPSIGNER || walletModel == WalletModel.SATSCARD) {
            return new CkCardApi(walletModel, pin);
        }

        throw new IllegalArgumentException("Cannot create card API for " + walletModel.toDisplayString());
    }

    public abstract boolean isInitialized() throws CardException;

    public abstract void initialize(byte[] entropy) throws CardException;

    public abstract WalletModel getCardType() throws CardException;

    public abstract Service<Void> getAuthDelayService() throws CardException;

    public abstract boolean requiresBackup() throws CardException;

    public abstract Service<String> getBackupService();

    public abstract boolean changePin(String newPin) throws CardException;

    public abstract Keystore getKeystore() throws CardException;

    public abstract Service<Void> getInitializationService(byte[] entropy);

    public abstract Service<Keystore> getImportService(List<ChildNumber> derivation, StringProperty messageProperty);

    public abstract Service<PSBT> getSignService(Wallet wallet, PSBT psbt, StringProperty messageProperty);

    public abstract Service<String> getSignMessageService(String message, ScriptType scriptType, List<ChildNumber> derivation, StringProperty messageProperty);

    public abstract void disconnect();

    public static boolean isReaderAvailable() {
        try {
            TerminalFactory tf = TerminalFactory.getDefault();
            return !tf.terminals().list().isEmpty();
        } catch(Exception e) {
            log.error("Error detecting card terminals", e);
        }

        return false;
    }
}
