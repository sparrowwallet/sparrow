package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Transaction;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CoinLabel extends CopyableLabel {
    public static final int MAX_SATS_SHOWN = 1000000;

    public static final DecimalFormat BTC_FORMAT = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    private final LongProperty value = new SimpleLongProperty();
    private Tooltip tooltip;
    private CoinContextMenu contextMenu;

    public CoinLabel() {
        this("Unknown");
    }

    public CoinLabel(String text) {
        super(text);
        BTC_FORMAT.setMaximumFractionDigits(8);
        valueProperty().addListener((observable, oldValue, newValue) -> setValueAsText((Long)newValue));
        tooltip = new Tooltip();
        contextMenu = new CoinContextMenu();
    }

    public final LongProperty valueProperty() {
        return value;
    }

    public final long getValue() {
        return value.get();
    }

    public final void setValue(long value) {
        this.value.set(value);
    }

    private void setValueAsText(Long value) {
        setTooltip(tooltip);
        setContextMenu(contextMenu);

        String satsValue = String.format(Locale.ENGLISH, "%,d",value) + " sats";
        String btcValue = BTC_FORMAT.format(value.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";
        if(value > MAX_SATS_SHOWN) {
            tooltip.setText(satsValue);
            setText(btcValue);
        } else {
            tooltip.setText(btcValue);
            setText(satsValue);
        }
    }

    private class CoinContextMenu extends ContextMenu {
        private MenuItem copySatsValue;
        private MenuItem copyBtcValue;

        public CoinContextMenu() {
            copySatsValue = new MenuItem("Copy Value in Satoshis");
            copySatsValue.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(Long.toString(getValue()));
                Clipboard.getSystemClipboard().setContent(content);
            });

            copyBtcValue = new MenuItem("Copy Value in BTC");
            copyBtcValue.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(BTC_FORMAT.format((double)getValue() / Transaction.SATOSHIS_PER_BITCOIN));
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copySatsValue, copyBtcValue);
        }
    }

    public static DecimalFormat getBTCFormat() {
        BTC_FORMAT.setMaximumFractionDigits(8);
        return BTC_FORMAT;
    }
}
