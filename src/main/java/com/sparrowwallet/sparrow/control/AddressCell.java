package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import org.controlsfx.glyphfont.Glyph;

public class AddressCell extends TreeTableCell<Entry, UtxoEntry.AddressStatus> {
    public AddressCell() {
        super();
        setAlignment(Pos.CENTER_LEFT);
        setContentDisplay(ContentDisplay.RIGHT);
        getStyleClass().add("address-cell");
    }

    @Override
    protected void updateItem(UtxoEntry.AddressStatus addressStatus, boolean empty) {
        super.updateItem(addressStatus, empty);

        UtxoEntry utxoEntry = addressStatus == null ? null : addressStatus.getUtxoEntry();
        EntryCell.applyRowStyles(this, utxoEntry);

        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            if(utxoEntry != null) {
                Address address = addressStatus.getAddress();
                setText(address.toString());
                setContextMenu(new EntryCell.AddressContextMenu(address, utxoEntry.getOutputDescriptor(), new NodeEntry(utxoEntry.getWallet(), utxoEntry.getNode())));
                Tooltip tooltip = new Tooltip();
                tooltip.setText(getTooltipText(utxoEntry, addressStatus.isDuplicate()));
                setTooltip(tooltip);

                if(addressStatus.isDuplicate()) {
                    setGraphic(getDuplicateGlyph());
                } else {
                    setGraphic(null);
                }
            }
        }
    }

    private String getTooltipText(UtxoEntry utxoEntry, boolean duplicate) {
        return (utxoEntry.getNode().getWallet().isNested() ? utxoEntry.getNode().getWallet().getDisplayName() + " " : "" ) +
                utxoEntry.getNode().toString() + (duplicate ? " (Duplicate address)" : "");
    }

    public static Glyph getDuplicateGlyph() {
        Glyph duplicateGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        duplicateGlyph.getStyleClass().add("duplicate-warning");
        duplicateGlyph.setFontSize(12);
        return duplicateGlyph;
    }
}
