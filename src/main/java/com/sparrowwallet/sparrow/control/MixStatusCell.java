package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.geometry.Pos;
import javafx.scene.control.*;

public class MixStatusCell extends TreeTableCell<Entry, UtxoEntry.MixStatus> {
    public MixStatusCell() {
        super();
        setAlignment(Pos.CENTER_RIGHT);
        setContentDisplay(ContentDisplay.LEFT);
        setGraphicTextGap(8);
        getStyleClass().add("mixstatus-cell");
    }

    @Override
    protected void updateItem(UtxoEntry.MixStatus mixStatus, boolean empty) {
        super.updateItem(mixStatus, empty);

        EntryCell.applyRowStyles(this, mixStatus == null ? null : mixStatus.getUtxoEntry());

        if(empty || mixStatus == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(Integer.toString(mixStatus.getMixesDone()));
            setContextMenu(null);
            setGraphic(null);
            setTooltip(null);
        }
    }
}
