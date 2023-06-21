package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import static com.sparrowwallet.sparrow.control.EntryCell.HashIndexEntryContextMenu;

public class DateCell extends TreeTableCell<Entry, Entry> {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public DateCell() {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.RIGHT);
        getStyleClass().add("date-cell");
    }

    @Override
    protected void updateItem(Entry entry, boolean empty) {
        super.updateItem(entry, empty);

        EntryCell.applyRowStyles(this, entry);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            if(entry instanceof UtxoEntry) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                if(utxoEntry.getHashIndex().getHeight() <= 0) {
                    setText("Unconfirmed " + (utxoEntry.getHashIndex().getHeight() < 0 ? "Parent " : "") + (utxoEntry.getWallet().isWhirlpoolMixWallet() ? "(Not yet mixable)" : (utxoEntry.isSpendable() ? "(Spendable)" : "(Not yet spendable)")));
                    setContextMenu(new HashIndexEntryContextMenu(getTreeTableView(), utxoEntry));
                } else if(utxoEntry.getHashIndex().getDate() != null) {
                    String date = DATE_FORMAT.format(utxoEntry.getHashIndex().getDate());
                    setText(date);
                    setContextMenu(new DateContextMenu(getTreeTableView(), utxoEntry, date));
                } else {
                    setText("Unknown");
                    setContextMenu(null);
                }

                Tooltip tooltip = new Tooltip();
                tooltip.setShowDelay(Duration.millis(250));
                int height = utxoEntry.getHashIndex().getHeight();
                tooltip.setText(height > 0 ? Integer.toString(height) : "Mempool");
                setTooltip(tooltip);
            }
            setGraphic(null);
        }
    }

    private static class DateContextMenu extends HashIndexEntryContextMenu {
        public DateContextMenu(TreeTableView<Entry> treeTableView, UtxoEntry utxoEntry, String date) {
            super(treeTableView, utxoEntry);

            MenuItem copyDate = new MenuItem("Copy Date");
            copyDate.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(date);
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyHeight = new MenuItem("Copy Block Height");
            copyHeight.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(utxoEntry.getHashIndex().getHeight() > 0 ? Integer.toString(utxoEntry.getHashIndex().getHeight()) : "Mempool");
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyDate, copyHeight);
        }
    }
}