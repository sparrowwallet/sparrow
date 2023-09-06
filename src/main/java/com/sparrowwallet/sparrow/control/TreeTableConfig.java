package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.scene.control.TreeTableColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class TreeTableConfig {
    private final List<TreeTableColumnConfig> columnConfigs;

    public TreeTableConfig(CoinTreeTable treeTable) {
        columnConfigs = new ArrayList<>();
        TreeTableColumn<Entry, ?> sortColumn = treeTable.getSortOrder().isEmpty() ? null : treeTable.getSortOrder().get(0);
        for(int i = 0; i < treeTable.getColumns().size(); i++) {
            TreeTableColumn<Entry, ?> column = treeTable.getColumns().get(i);
            //TODO: Support column widths
            columnConfigs.add(new TreeTableColumnConfig(i, null, sortColumn == column ? column.getSortType() : null));
        }
    }

    public TreeTableConfig(List<TreeTableColumnConfig> columnConfigs) {
        this.columnConfigs = columnConfigs;
    }

    public String toString() {
        StringJoiner joiner = new StringJoiner("|");
        columnConfigs.stream().forEach(col -> joiner.add(col.toString()));
        return joiner.toString();
    }

    public static TreeTableConfig fromString(String tableConfig) {
        List<TreeTableColumnConfig> columnConfigs = new ArrayList<>();
        String[] parts = tableConfig.split("\\|");
        for(String part : parts) {
            columnConfigs.add(TreeTableColumnConfig.fromString(part));
        }

        return new TreeTableConfig(columnConfigs);
    }
}
