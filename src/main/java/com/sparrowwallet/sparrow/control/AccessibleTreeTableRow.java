package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.control.TreeTableRow;

import java.util.ArrayList;
import java.util.List;

public class AccessibleTreeTableRow extends TreeTableRow<Entry> {
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if(attribute == AccessibleAttribute.TEXT && !isEmpty()) {
            TreeTablePosition<Entry, ?> focusedCell = getTreeTableView().getFocusModel().getFocusedCell();
            TreeTableColumn<Entry, ?> focusedColumn = focusedCell == null ? null : focusedCell.getTableColumn();
            List<String> values = new ArrayList<>();
            for(Node child : getChildrenUnmodifiable()) {
                if(child instanceof TreeTableCell<?, ?> cell && cell.isVisible()) {
                    String column = cell.getTableColumn() == null ? null : cell.getTableColumn().getText();
                    String value = cell.getText();
                    if(cell.getTableColumn() == focusedColumn && column != null && !column.isBlank()) {
                        return column + ", " + (value == null || value.isBlank() ? "blank" : value);
                    } else if(column != null && !column.isBlank()) {
                        values.add(column + ", " + (value == null || value.isBlank() ? "blank" : value));
                    }
                }
            }

            if(!values.isEmpty()) {
                return String.join("; ", values);
            }
        }

        return super.queryAccessibleAttribute(attribute, parameters);
    }
}
