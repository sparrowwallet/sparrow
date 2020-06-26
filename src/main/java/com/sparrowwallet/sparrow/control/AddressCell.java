package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;

public class AddressCell extends TreeTableCell<Entry, Entry> {
    public AddressCell() {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.RIGHT);
    }

    @Override
    protected void updateItem(Entry entry, boolean empty) {
        super.updateItem(entry, empty);

        EntryCell.applyRowStyles(this, entry);
        getStyleClass().add("address-cell");

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            if(entry instanceof UtxoEntry) {
                UtxoEntry utxoEntry = (UtxoEntry)entry;
                Address address = utxoEntry.getAddress();
                setText(address.toString());
                setContextMenu(new EntryCell.AddressContextMenu(address, utxoEntry.getOutputDescriptor()));
                Tooltip tooltip = new Tooltip();
                tooltip.setText(utxoEntry.getNode().getDerivationPath());
                setTooltip(tooltip);
            }
            setGraphic(null);
        }
    }
}
