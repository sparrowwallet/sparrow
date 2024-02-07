package com.sparrowwallet.sparrow.io;

import com.google.common.base.Throwables;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.ckcard.CkCardApi;
import com.sparrowwallet.sparrow.io.satochip.SatoCardApi;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class CardApi {
    private static final Logger log = LoggerFactory.getLogger(CardApi.class);

    private static File[] LINUX_PCSC_LIBS = new File[] {
            new File("/usr/lib/libpcsclite.so.1"),
            new File("/usr/local/lib/libpcsclite.so.1"),
            new File("/lib/x86_64-linux-gnu/libpcsclite.so.1"),
            new File("/lib/aarch64-linux-gnu/libpcsclite.so.1"),
            new File("/usr/lib64/libpcsclite.so.1"),
            new File("/usr/lib/x86_64-linux-gnu/libpcsclite.so.1")};

    private static boolean initialized;

    public static List<WalletModel> getConnectedCards() throws CardException {
        List<WalletModel> cards = new ArrayList<>();

        try {
            CkCardApi ckCardApi = new CkCardApi(null, null);
            cards.add(ckCardApi.getCardType());
        } catch(Exception e) {
            //ignore
        }

        try {
            SatoCardApi satoCardApi = new SatoCardApi(null, null);
            cards.add(satoCardApi.getCardType());
        } catch(Exception e) {
            //ignore
        }

        return cards;
    }

    public static CardApi getCardApi(WalletModel walletModel, String pin) throws CardException {
        if(walletModel == WalletModel.TAPSIGNER || walletModel == WalletModel.SATSCHIP || walletModel == WalletModel.SATSCARD) {
            return new CkCardApi(walletModel, pin);
        }

        if(walletModel == WalletModel.SATOCHIP) {
            return new SatoCardApi(walletModel, pin);
        }

        throw new IllegalArgumentException("Cannot create card API for " + walletModel.toDisplayString());
    }

    public abstract boolean isInitialized() throws CardException;

    public abstract void initialize(int slot, byte[] entropy) throws CardException;

    public abstract WalletModel getCardType() throws CardException;

    public abstract ScriptType getDefaultScriptType();

    public abstract int getCurrentSlot() throws CardException;

    public abstract Service<Void> getAuthDelayService() throws CardException;

    public abstract boolean requiresBackup() throws CardException;

    public abstract Service<String> getBackupService();

    public abstract boolean changePin(String newPin) throws CardException;

    public abstract Keystore getKeystore() throws CardException;

    public abstract Service<Void> getInitializationService(byte[] entropy, StringProperty messageProperty);

    public abstract Service<Keystore> getImportService(List<ChildNumber> derivation, StringProperty messageProperty);

    public abstract Service<PSBT> getSignService(Wallet wallet, PSBT psbt, StringProperty messageProperty);

    public abstract Service<String> getSignMessageService(String message, ScriptType scriptType, List<ChildNumber> derivation, StringProperty messageProperty);

    public abstract Service<ECKey> getPrivateKeyService(Integer slot, StringProperty messageProperty);

    public abstract Service<Address> getAddressService(StringProperty messageProperty);

    public abstract void disconnect();

    public static boolean isReaderAvailable() {
        return !getAvailableTerminals().isEmpty();
    }

    public static List<CardTerminal> getAvailableTerminals() {
        setLibrary();

        try {
            TerminalFactory tf = TerminalFactory.getDefault();
            return tf.terminals().list();
        } catch(Exception e) {
            Throwable cause = Throwables.getRootCause(e);
            if(cause.getMessage().equals("SCARD_E_NO_SERVICE")) {
                recoverNoService();
            } else if(cause.getMessage().equals("SCARD_E_NO_READERS_AVAILABLE")) {
                log.info("Error detecting card terminals", e);
            } else {
                log.error("Error detecting card terminals", e);
            }
        }

        return Collections.emptyList();
    }

    private static void recoverNoService() {
        try {
            Class<?> pcscterminal = Class.forName("sun.security.smartcardio.PCSCTerminals");
            Field contextId = pcscterminal.getDeclaredField("contextId");
            contextId.setAccessible(true);

            if(contextId.getLong(pcscterminal) != 0L)
            {
                // First get a new context value
                Class<?> pcsc = Class.forName("sun.security.smartcardio.PCSC");
                Method SCardEstablishContext = pcsc.getDeclaredMethod(
                        "SCardEstablishContext",
                        Integer.TYPE);
                SCardEstablishContext.setAccessible(true);

                Field SCARD_SCOPE_USER = pcsc.getDeclaredField("SCARD_SCOPE_USER");
                SCARD_SCOPE_USER.setAccessible(true);

                long newId = ((Long)SCardEstablishContext.invoke(pcsc,
                        new Object[] { SCARD_SCOPE_USER.getInt(pcsc) }
                ));
                contextId.setLong(pcscterminal, newId);


                // Then clear the terminals in cache
                TerminalFactory factory = TerminalFactory.getDefault();
                CardTerminals terminals = factory.terminals();
                Field fieldTerminals = pcscterminal.getDeclaredField("terminals");
                fieldTerminals.setAccessible(true);
                Class<?> classMap = Class.forName("java.util.Map");
                Method clearMap = classMap.getDeclaredMethod("clear");

                clearMap.invoke(fieldTerminals.get(terminals));
            }
        } catch(Exception e) {
            log.error("Failed to recover card service", e);
        }
    }

    private static void setLibrary() {
        if(!initialized && Platform.getCurrent() == Platform.UNIX) {
            for(File lib : LINUX_PCSC_LIBS) {
                if(lib.exists()) {
                    System.setProperty("sun.security.smartcardio.library", lib.getAbsolutePath());
                    break;
                }
            }
        }

        initialized = true;
    }
}
