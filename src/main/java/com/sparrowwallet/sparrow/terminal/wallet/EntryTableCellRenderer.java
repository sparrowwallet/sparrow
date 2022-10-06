package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.gui2.table.DefaultTableCellRenderer;
import com.sparrowwallet.sparrow.terminal.wallet.table.TableCell;

public class EntryTableCellRenderer extends DefaultTableCellRenderer<TableCell> {
    @Override
    protected String[] getContent(TableCell cell) {
        String[] lines;
        if(cell == null) {
            lines = new String[] { "" };
        } else {
            lines = new String[] { cell.formatCell() };
        }

        return lines;
    }
}
