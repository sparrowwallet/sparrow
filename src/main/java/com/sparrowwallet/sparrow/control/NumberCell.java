package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.scene.control.TreeTableCell;
import org.controlsfx.tools.Platform;

public class NumberCell extends TreeTableCell<Entry, Number> {
    public NumberCell() {
        super();
        getStyleClass().add("number-cell");
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
        } else {
            setText(amount.toString());
            setGraphic(null);
        }
    }
}
