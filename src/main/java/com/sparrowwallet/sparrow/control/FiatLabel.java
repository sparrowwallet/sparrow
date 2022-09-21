package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.property.*;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.math.BigDecimal;
import java.util.Currency;

public class FiatLabel extends CopyableLabel {
    private final LongProperty valueProperty = new SimpleLongProperty(-1);
    private final DoubleProperty btcRateProperty = new SimpleDoubleProperty(0.0);
    private final ObjectProperty<Currency> currencyProperty = new SimpleObjectProperty<>(null);
    private final Tooltip tooltip;
    private final FiatContextMenu contextMenu;

    public FiatLabel() {
        this("");
    }

    public FiatLabel(String text) {
        super(text);
        valueProperty().addListener((observable, oldValue, newValue) -> setValueAsText((Long)newValue, Config.get().getUnitFormat()));
        btcRateProperty().addListener((observable, oldValue, newValue) -> setValueAsText(getValue(), Config.get().getUnitFormat()));
        currencyProperty().addListener((observable, oldValue, newValue) -> setValueAsText(getValue(), Config.get().getUnitFormat()));
        tooltip = new Tooltip();
        contextMenu = new FiatContextMenu();
    }

    public final LongProperty valueProperty() {
        return valueProperty;
    }

    public final long getValue() {
        return valueProperty.get();
    }

    public final void setValue(long value) {
        this.valueProperty.set(value);
    }

    public final DoubleProperty btcRateProperty() {
        return btcRateProperty;
    }

    public final double getBtcRate() {
        return btcRateProperty.get();
    }

    public final void setBtcRate(double btcRate) {
        this.btcRateProperty.set(btcRate);
    }

    public final ObjectProperty<Currency> currencyProperty() {
        return currencyProperty;
    }

    public final Currency getCurrency() {
        return currencyProperty.get();
    }

    public final void setCurrency(Currency currency) {
        this.currencyProperty.set(currency);
    }

    public final void set(CurrencyRate currencyRate, long value) {
        set(currencyRate.getCurrency(), currencyRate.getBtcRate(), value);
    }

    public final void set(Currency currency, double btcRate, long value) {
        setValue(value);
        setBtcRate(btcRate);
        setCurrency(currency);
    }

    public void refresh() {
        refresh(Config.get().getUnitFormat());
    }

    public void refresh(UnitFormat unitFormat) {
        setValueAsText(getValue(), unitFormat);
    }

    private void setValueAsText(long balance, UnitFormat unitFormat) {
        if(getCurrency() != null && getBtcRate() > 0.0) {
            BigDecimal satsBalance = BigDecimal.valueOf(balance);
            BigDecimal btcBalance = satsBalance.divide(BigDecimal.valueOf(Transaction.SATOSHIS_PER_BITCOIN));
            BigDecimal fiatBalance = btcBalance.multiply(BigDecimal.valueOf(getBtcRate()));

            String label = getCurrency().getSymbol() + " " + unitFormat.formatCurrencyValue(fiatBalance.doubleValue());
            tooltip.setText("1 BTC = " + getCurrency().getSymbol() + " " + unitFormat.formatCurrencyValue(getBtcRate()));

            setText(label);
            setTooltip(tooltip);
            setContextMenu(contextMenu);
        } else {
            setText("");
            setTooltip(null);
            setContextMenu(null);
        }
    }

    private class FiatContextMenu extends ContextMenu {
        public FiatContextMenu() {
            MenuItem copyValue = new MenuItem("Copy Value");
            copyValue.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(getText());
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyRate = new MenuItem("Copy Rate");
            copyRate.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(getTooltip().getText());
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyValue, copyRate);
        }
    }
}
