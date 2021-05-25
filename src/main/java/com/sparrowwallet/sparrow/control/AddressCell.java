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
                setContextMenu(new EntryCell.AddressContextMenu(address, utxoEntry.getOutputDescriptor(), new NodeEntry(utxoEntry.getWallet(), utxoEntry.getNode())));
                Tooltip tooltip = new Tooltip();
                tooltip.setText(getTooltipText(utxoEntry));
                setTooltip(tooltip);

                if(utxoEntry.isDuplicateAddress()) {
                    setGraphic(getDuplicateGlyph());
                } else {
                    setGraphic(null);
                }

                utxoEntry.duplicateAddressProperty().addListener((observable, oldValue, newValue) -> {
                    if(newValue) {
                        setGraphic(getDuplicateGlyph());
                        Tooltip tt = new Tooltip();
                        tt.setText(getTooltipText(utxoEntry));
                        setTooltip(tt);
                    } else {
                        setGraphic(null);
                    }
                });
            }
        }
    }

    private String getTooltipText(UtxoEntry utxoEntry) {
        return utxoEntry.getNode().getDerivationPath().replace("m", "..") + (utxoEntry.isDuplicateAddress() ? " (Duplicate address)" : "");
    }

    public static Glyph getDuplicateGlyph() {
        Glyph duplicateGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        duplicateGlyph.getStyleClass().add("duplicate-warning");
        duplicateGlyph.setFontSize(12);
        return duplicateGlyph;
    }
}
