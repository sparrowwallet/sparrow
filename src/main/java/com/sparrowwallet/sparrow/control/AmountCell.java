package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.layout.Region;

import java.util.Locale;

class AmountCell extends TreeTableCell<Entry, Number> {
    public AmountCell() {
        super();
        getStyleClass().add("amount-cell");
    }

    @Override
    protected void updateItem(Number amount, boolean empty) {
        super.updateItem(amount, empty);

        if(empty || amount == null) {
            setText(null);
            setGraphic(null);
        } else {
            Entry entry = getTreeTableView().getTreeItem(getIndex()).getValue();
            EntryCell.applyRowStyles(this, entry);

            String satsValue = String.format(Locale.ENGLISH, "%,d", amount.longValue());
            final String btcValue = CoinLabel.getBTCFormat().format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";

            if(entry instanceof TransactionEntry) {
                TransactionEntry transactionEntry = (TransactionEntry)entry;
                Tooltip tooltip = new Tooltip();
                tooltip.setText(btcValue + " (" + transactionEntry.getConfirmationsDescription() + ")");
                setTooltip(tooltip);

                transactionEntry.confirmationsProperty().addListener((observable, oldValue, newValue) -> {
                    Tooltip newTooltip = new Tooltip();
                    newTooltip.setText(btcValue + " (" + transactionEntry.getConfirmationsDescription() + ")");
                    setTooltip(newTooltip);
                });

                if(transactionEntry.isConfirming()) {
                    ConfirmationProgressIndicator arc = new ConfirmationProgressIndicator(transactionEntry.getConfirmations());
                    arc.confirmationsProperty().bind(transactionEntry.confirmationsProperty());
                    setGraphic(arc);
                    setContentDisplay(ContentDisplay.LEFT);
                } else {
                    setGraphic(null);
                }
            } else if(entry instanceof HashIndexEntry) {
                Region node = new Region();
                node.setPrefWidth(10);
                setGraphic(node);
                setContentDisplay(ContentDisplay.RIGHT);

                if(((HashIndexEntry) entry).getType() == HashIndexEntry.Type.INPUT) {
                    satsValue = "-" + satsValue;
                }
            } else {
                setGraphic(null);
            }

            if(getTooltip() == null) {
                Tooltip tooltip = new Tooltip();
                tooltip.setText(btcValue);
                setTooltip(tooltip);
            }

            setText(satsValue);
        }
    }
}
