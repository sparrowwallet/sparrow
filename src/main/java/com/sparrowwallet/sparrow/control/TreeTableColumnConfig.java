package com.sparrowwallet.sparrow.control;

import javafx.scene.control.TreeTableColumn;

public class TreeTableColumnConfig {
    private final int index;
    private final Integer width;
    private final TreeTableColumn.SortType sortType;

    public TreeTableColumnConfig(int index, Integer width, TreeTableColumn.SortType sortType) {
        this.index = index;
        this.width = width;
        this.sortType = sortType;
    }

    public TreeTableColumnConfig(int index, String width, String sortType) {
        this.index = index;
        this.width = width.isEmpty() ? null : Integer.valueOf(width, 10);
        this.sortType = sortType.isEmpty() ? null : TreeTableColumn.SortType.valueOf(sortType);
    }

    public int getIndex() {
        return index;
    }

    public Integer getWidth() {
        return width;
    }

    public TreeTableColumn.SortType getSortType() {
        return sortType;
    }

    public String toString() {
        return index + "-" + (width == null ? "" : width) + "-" + (sortType == null ? "" : sortType);
    }

    public static TreeTableColumnConfig fromString(String columnConfig) {
        String[] parts = columnConfig.split("-", 3);
        if(parts.length == 3) {
            return new TreeTableColumnConfig(Integer.parseInt(parts[0]), parts[1], parts[2]);
        }

        return new TreeTableColumnConfig(Integer.parseInt(parts[0]), (Integer)null, null);
    }
}
