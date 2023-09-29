package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Status;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletUtxoStatusChangedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;

import java.util.Collections;

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
                setContextMenu(new EntryCell.AddressContextMenu(address, utxoEntry.getOutputDescriptor(), new NodeEntry(utxoEntry.getWallet(), utxoEntry.getNode()), false, getTreeTableView()));
                Tooltip tooltip = new Tooltip();
                tooltip.setShowDelay(Duration.millis(250));
                tooltip.setText(getTooltipText(utxoEntry, addressStatus.isDuplicate(), addressStatus.isDustAttack()));
                setTooltip(tooltip);

                if(addressStatus.isDustAttack()) {
                    setGraphic(getDustAttackHyperlink(utxoEntry));
                } else if(addressStatus.isDuplicate()) {
                    setGraphic(getDuplicateGlyph());
                } else {
                    setGraphic(null);
                }
            }
        }
    }

    private String getTooltipText(UtxoEntry utxoEntry, boolean duplicate, boolean dustAttack) {
        return (utxoEntry.getNode().getWallet().isNested() ? utxoEntry.getNode().getWallet().getDisplayName() + " " : "" ) +
                utxoEntry.getNode().toString() + (duplicate ? " (Duplicate address)" : (dustAttack ? " (Possible dust attack)" : ""));
    }

    public static Glyph getDuplicateGlyph() {
        Glyph duplicateGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        duplicateGlyph.getStyleClass().add("duplicate-warning");
        duplicateGlyph.setFontSize(12);
        return duplicateGlyph;
    }

    public static Hyperlink getDustAttackHyperlink(UtxoEntry utxoEntry) {
        Glyph dustAttackGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_TRIANGLE);
        dustAttackGlyph.getStyleClass().add("dust-attack-warning");
        dustAttackGlyph.setFontSize(12);

        Hyperlink hyperlink = new Hyperlink(utxoEntry.getHashIndex().getStatus() == Status.FROZEN ? "" : "Freeze?", dustAttackGlyph);
        hyperlink.getStyleClass().add("freeze-dust-utxo");
        hyperlink.setOnAction(event -> {
            if(utxoEntry.getHashIndex().getStatus() != Status.FROZEN) {
                hyperlink.setText("");
                utxoEntry.getHashIndex().setStatus(Status.FROZEN);
                EventManager.get().post(new WalletUtxoStatusChangedEvent(utxoEntry.getWallet(), Collections.singletonList(utxoEntry.getHashIndex())));
            }
        });

        return hyperlink;
    }
}
