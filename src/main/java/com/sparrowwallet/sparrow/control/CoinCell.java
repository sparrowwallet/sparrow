package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.controlsfx.tools.Platform;

import java.text.DecimalFormat;

class CoinCell extends TreeTableCell<Entry, Number> {
    private final Tooltip tooltip;

    public CoinCell() {
        super();
        tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.millis(500));
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
        } else {
            Entry entry = getTreeTableView().getTreeItem(getIndex()).getValue();
            EntryCell.applyRowStyles(this, entry);

            CoinTreeTable coinTreeTable = (CoinTreeTable)getTreeTableView();
            UnitFormat format = coinTreeTable.getUnitFormat();
            BitcoinUnit unit = coinTreeTable.getBitcoinUnit();

            String satsValue = format.formatSatsValue(amount.longValue());
            DecimalFormat decimalFormat = (amount.longValue() == 0L ? format.getBtcFormat() : format.getTableBtcFormat());
            final String btcValue = decimalFormat.format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN);

            if(unit.equals(BitcoinUnit.BTC)) {
                tooltip.setText(satsValue + " " + BitcoinUnit.SATOSHIS.getLabel());
                setText(btcValue);
            } else {
                tooltip.setText(btcValue + " " + BitcoinUnit.BTC.getLabel());
                setText(satsValue);
            }
            setTooltip(tooltip);
            String tooltipValue = tooltip.getText();

            if(entry instanceof TransactionEntry transactionEntry) {
                tooltip.setText(tooltipValue + " (" + transactionEntry.getConfirmationsDescription() + ")");

                transactionEntry.confirmationsProperty().addListener((observable, oldValue, newValue) -> {
                    tooltip.setText(tooltipValue + " (" + transactionEntry.getConfirmationsDescription() + ")");
                });

                if(transactionEntry.isConfirming()) {
                    ConfirmationProgressIndicator arc = new ConfirmationProgressIndicator(transactionEntry.getConfirmations());
                    arc.confirmationsProperty().bind(transactionEntry.confirmationsProperty());
                    setGraphic(arc);
                    setContentDisplay(ContentDisplay.LEFT);
                } else {
                    setGraphic(null);
                }

                if(amount.longValue() < 0) {
                    getStyleClass().add("negative-amount");
                }
            } else if(entry instanceof UtxoEntry) {
                setGraphic(null);
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
        }
    }
}
