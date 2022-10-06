package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.base.Strings;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.event.WalletHistoryClearedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;

import java.util.Currency;

public class WalletDialog extends DialogWindow {
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
        WalletActionsDialog walletActionsDialog = new WalletActionsDialog(getWalletForm().getWallet());
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

    protected double getFiatValue(long satsValue, Double btcRate) {
        return satsValue * btcRate / Transaction.SATOSHIS_PER_BITCOIN;
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
