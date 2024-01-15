package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.controlsfx.tools.Platform;

import java.text.DecimalFormat;

class CoinCell extends TreeTableCell<Entry, Number> implements ConfirmationsListener {
    private final CoinTooltip tooltip;
    private final CoinContextMenu contextMenu;

    private IntegerProperty confirmationsProperty;

    public CoinCell() {
        super();
        tooltip = new CoinTooltip();
        tooltip.setShowDelay(Duration.millis(500));
        contextMenu = new CoinContextMenu();
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

            CoinTreeTable coinTreeTable = (CoinTreeTable)getTreeTableView();
            UnitFormat format = coinTreeTable.getUnitFormat();
            BitcoinUnit unit = coinTreeTable.getBitcoinUnit();

            String satsValue = format.formatSatsValue(amount.longValue());
            DecimalFormat decimalFormat = (amount.longValue() == 0L ? format.getBtcFormat() : format.getTableBtcFormat());
            final String btcValue = decimalFormat.format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN);

            if(unit.equals(BitcoinUnit.BTC)) {
                tooltip.setValue(satsValue + " " + BitcoinUnit.SATOSHIS.getLabel());
                setText(btcValue);
            } else {
                tooltip.setValue(btcValue + " " + BitcoinUnit.BTC.getLabel());
                setText(satsValue);
            }
            setTooltip(tooltip);
            contextMenu.updateAmount(amount);
            setContextMenu(contextMenu);

            if(entry instanceof TransactionEntry transactionEntry) {
                tooltip.showConfirmations(transactionEntry.confirmationsProperty(), transactionEntry.isCoinbase());

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

    @Override
    public IntegerProperty getConfirmationsProperty() {
        if(confirmationsProperty == null) {
            confirmationsProperty = new SimpleIntegerProperty();
            confirmationsProperty.addListener((observable, oldValue, newValue) -> {
                if(newValue.intValue() >= BlockTransactionHash.BLOCKS_TO_CONFIRM) {
                    getStyleClass().remove("confirming");
                    confirmationsProperty.unbind();
                }
            });
        }

        return confirmationsProperty;
    }

    private static final class CoinTooltip extends Tooltip {
        private final IntegerProperty confirmationsProperty = new SimpleIntegerProperty();
        private boolean showConfirmations;
        private boolean isCoinbase;
        private String value;

        public void setValue(String value) {
            this.value = value;
            setTooltipText();
        }

        public void showConfirmations(IntegerProperty txEntryConfirmationsProperty, boolean coinbase) {
            showConfirmations = true;
            isCoinbase = coinbase;

            int confirmations = txEntryConfirmationsProperty.get();
            if(confirmations < BlockTransactionHash.BLOCKS_TO_FULLY_CONFIRM) {
                confirmationsProperty.bind(txEntryConfirmationsProperty);
                confirmationsProperty.addListener((observable, oldValue, newValue) -> {
                    setTooltipText();
                    if(newValue.intValue() >= BlockTransactionHash.BLOCKS_TO_FULLY_CONFIRM) {
                        confirmationsProperty.unbind();
                    }
                });
            } else {
                confirmationsProperty.unbind();
                confirmationsProperty.set(confirmations);
            }

            setTooltipText();
        }

        private void setTooltipText() {
            setText(value + (showConfirmations ? " (" + getConfirmationsDescription() + ")" : ""));
        }

        public String getConfirmationsDescription() {
            int confirmations = confirmationsProperty.get();
            if(confirmations == 0) {
                return "Unconfirmed in mempool";
            } else if(confirmations < BlockTransactionHash.BLOCKS_TO_FULLY_CONFIRM) {
                return confirmations + " confirmation" + (confirmations == 1 ? "" : "s") + (isCoinbase ? ", immature coinbase" : "");
            } else {
                return BlockTransactionHash.BLOCKS_TO_FULLY_CONFIRM + "+ confirmations";
            }
        }
    }

    private static class CoinContextMenu extends ContextMenu {
        private Number amount;

        public void updateAmount(Number amount) {
            if(amount.equals(this.amount)) {
                return;
            }

            this.amount = amount;
            getItems().clear();

            MenuItem copySatsValue = new MenuItem("Copy Value in sats");
            copySatsValue.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(amount.toString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyBtcValue = new MenuItem("Copy Value in BTC");
            copyBtcValue.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
                content.putString(format.formatBtcValue(amount.longValue()));
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copySatsValue, copyBtcValue);
        }
    }
}
