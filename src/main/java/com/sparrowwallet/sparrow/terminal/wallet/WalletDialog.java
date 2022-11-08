package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.base.Strings;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.event.ChildWalletsAddedEvent;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryClearedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.whirlpool.WhirlpoolServices;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Currency;
import java.util.List;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class WalletDialog extends DialogWindow {
    private static final Logger log = LoggerFactory.getLogger(WalletDialog.class);

    private final WalletForm walletForm;

    public WalletDialog(String title, WalletForm walletForm) {
        super(title);
        this.walletForm = walletForm;
    }

    public WalletForm getWalletForm() {
        return walletForm;
    }

    protected void onBack(Function function) {
        close();
        WalletActionsDialog walletActionsDialog = new WalletActionsDialog(getWalletForm().getWalletId());
        walletActionsDialog.setFunction(function);
        walletActionsDialog.showDialog(SparrowTerminal.get().getGui());
    }

    protected void onRefresh() {
        Wallet wallet = getWalletForm().getWallet();
        Wallet pastWallet = wallet.copy();
        wallet.clearHistory();
        AppServices.clearTransactionHistoryCache(wallet);
        Platform.runLater(() -> EventManager.get().post(new WalletHistoryClearedEvent(wallet, pastWallet, getWalletForm().getWalletId())));
    }

    @Override
    public void close() {
        if(getTextGUI() != null) {
            getTextGUI().removeWindow(this);
        }
    }

    protected void addAccount(Wallet masterWallet, StandardAccount standardAccount, Runnable postAddition) {
        if(masterWallet.isEncrypted()) {
            String walletId = getWalletForm().getWalletId();

            TextInputDialogBuilder builder = new TextInputDialogBuilder().setTitle("Wallet Password");
            builder.setDescription("Enter the wallet password:");
            builder.setPasswordInput(true);

            String password = builder.build().showDialog(SparrowTerminal.get().getGui());
            if(password != null) {
                Platform.runLater(() -> {
                    Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(getWalletForm().getStorage(), new SecureString(password), true);
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                        ECKey encryptionFullKey = keyDerivationService.getValue();
                        Key key = new Key(encryptionFullKey.getPrivKeyBytes(), getWalletForm().getStorage().getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                        encryptionFullKey.clear();
                        masterWallet.decrypt(key);
                        addAndEncryptAccount(masterWallet, standardAccount, key);
                        if(postAddition != null) {
                            postAddition.run();
                        }
                    });
                    keyDerivationService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                        if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                            showErrorDialog("Invalid Password", "The wallet password was invalid.");
                        } else {
                            log.error("Error deriving wallet key", keyDerivationService.getException());
                        }
                    });
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                    keyDerivationService.start();
                });
            }
        } else {
            Platform.runLater(() -> {
                addAndSaveAccount(masterWallet, standardAccount, null);
                if(postAddition != null) {
                    postAddition.run();
                }
            });
        }
    }

    private void addAndEncryptAccount(Wallet masterWallet, StandardAccount standardAccount, Key key) {
        try {
            addAndSaveAccount(masterWallet, standardAccount, key);
        } finally {
            masterWallet.encrypt(key);
            key.clear();
        }
    }

    private void addAndSaveAccount(Wallet masterWallet, StandardAccount standardAccount, Key key) {
        List<Wallet> childWallets;
        if(StandardAccount.WHIRLPOOL_ACCOUNTS.contains(standardAccount)) {
            childWallets = WhirlpoolServices.prepareWhirlpoolWallet(masterWallet, getWalletForm().getWalletId(), getWalletForm().getStorage());
        } else {
            Wallet childWallet = masterWallet.addChildWallet(standardAccount);
            EventManager.get().post(new ChildWalletsAddedEvent(getWalletForm().getStorage(), masterWallet, childWallet));
            childWallets = List.of(childWallet);
        }

        if(key != null) {
            for(Wallet childWallet : childWallets) {
                childWallet.encrypt(key);
            }
        }

        saveChildWallets(masterWallet);
    }

    private void saveChildWallets(Wallet masterWallet) {
        for(Wallet childWallet : masterWallet.getChildWallets()) {
            if(!childWallet.isNested()) {
                Storage storage = getWalletForm().getStorage();
                if(!storage.isPersisted(childWallet)) {
                    try {
                        storage.saveWallet(childWallet);
                    } catch(Exception e) {
                        log.error("Error saving wallet", e);
                        showErrorDialog("Error saving wallet " + childWallet.getName(), e.getMessage());
                    }
                }
            }
        }
    }

    protected String formatBitcoinValue(long value, boolean appendUnit) {
        BitcoinUnit unit = Config.get().getBitcoinUnit();
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = (value >= BitcoinUnit.getAutoThreshold() ? BitcoinUnit.BTC : BitcoinUnit.SATOSHIS);
        }

        UnitFormat format = Config.get().getUnitFormat();
        if(format == null) {
            format = UnitFormat.DOT;
        }

        return unit == BitcoinUnit.SATOSHIS ? format.formatSatsValue(value) + (appendUnit ? " sats" : "") : format.formatBtcValue(value) + (appendUnit ? " BTC" : "");
    }

    protected String formatFiatValue(Double value) {
        UnitFormat format = Config.get().getUnitFormat();
        if(format == null) {
            format = UnitFormat.DOT;
        }

        CurrencyRate currencyRate = AppServices.getFiatCurrencyExchangeRate();
        if(currencyRate != null && currencyRate.isAvailable() && value > 0) {
            Currency currency = currencyRate.getCurrency();
            return currency.getSymbol() + " " + format.formatCurrencyValue(value);
        } else {
            return "";
        }
    }

    protected double getFiatValue(long satsValue, CurrencyRate currencyRate) {
        if(currencyRate != null && currencyRate.isAvailable()) {
            return satsValue * currencyRate.getBtcRate() / Transaction.SATOSHIS_PER_BITCOIN;
        }

        return 0d;
    }

    protected static String centerPad(String text, int length) {
        if(text.length() >= length) {
            return text;
        }

        int excess = length - text.length();
        int half = excess / 2;
        int extra = excess % 2;

        return Strings.repeat(" ", half) + text + Strings.repeat(" ", half) + Strings.repeat(" ", extra);
    }
}
