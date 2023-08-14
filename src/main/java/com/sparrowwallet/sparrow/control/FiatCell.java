package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.controlsfx.tools.Platform;

import java.math.BigDecimal;
import java.util.Currency;

public class FiatCell extends TreeTableCell<Entry, Number> {
    private final Tooltip tooltip;
    private final FiatContextMenu contextMenu;

    public FiatCell() {
        super();
        tooltip = new Tooltip();
        contextMenu = new FiatContextMenu();
        getStyleClass().add("coin-cell");
        if(Platform.getCurrent() == Platform.OSX) {
            getStyleClass().add("number-field");
        }
    }

    @Override
    protected void updateItem(Number amount, boolean empty) {
        super.updateItem(amount, empty);

        if(empty || amount == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
        } else {
            Entry entry = getTreeTableView().getTreeItem(getIndex()).getValue();
            EntryCell.applyRowStyles(this, entry);

            CoinTreeTable coinTreeTable = (CoinTreeTable) getTreeTableView();
            UnitFormat format = coinTreeTable.getUnitFormat();
            CurrencyRate currencyRate = coinTreeTable.getCurrencyRate();

            if(currencyRate != null && currencyRate.isAvailable()) {
                Currency currency = currencyRate.getCurrency();
                double btcRate = currencyRate.getBtcRate();

                BigDecimal satsBalance = BigDecimal.valueOf(amount.longValue());
                BigDecimal btcBalance = satsBalance.divide(BigDecimal.valueOf(Transaction.SATOSHIS_PER_BITCOIN));
                BigDecimal fiatBalance = btcBalance.multiply(BigDecimal.valueOf(btcRate));

                String label = format.formatCurrencyValue(fiatBalance.doubleValue());
                tooltip.setText("1 BTC = " + currency.getSymbol() + " " + format.formatCurrencyValue(btcRate));

                setText(label);
                setGraphic(null);
                setTooltip(tooltip);
                setContextMenu(contextMenu);
            } else {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                setContextMenu(null);
            }
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