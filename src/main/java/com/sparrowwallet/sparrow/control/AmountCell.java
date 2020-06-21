package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.layout.Region;

import java.util.Locale;

class AmountCell extends TreeTableCell<Entry, Long> {
    public AmountCell() {
        super();
        getStyleClass().add("amount-cell");
        setContentDisplay(ContentDisplay.RIGHT);
    }

    @Override
    protected void updateItem(Long amount, boolean empty) {
        super.updateItem(amount, empty);

        if(empty || amount == null) {
            setText(null);
            setGraphic(null);
        } else {
            EntryCell.applyRowStyles(this, getTreeTableView().getTreeItem(getIndex()).getValue());

            String satsValue = String.format(Locale.ENGLISH, "%,d", amount);
            String btcValue = CoinLabel.getBTCFormat().format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";

            Entry entry = getTreeTableView().getTreeItem(getIndex()).getValue();
            if(entry instanceof HashIndexEntry) {
                Region node = new Region();
                node.setPrefWidth(10);
                setGraphic(node);

                if(((HashIndexEntry) entry).getType() == HashIndexEntry.Type.INPUT) {
                    satsValue = "-" + satsValue;
                }
            } else {
                setGraphic(null);
            }

            Tooltip tooltip = new Tooltip();
            tooltip.setText(btcValue);

            setText(satsValue);
            setTooltip(tooltip);
        }
    }
}
