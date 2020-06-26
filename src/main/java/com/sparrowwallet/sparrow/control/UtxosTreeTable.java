package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.wallet.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.util.List;

public class UtxosTreeTable extends TreeTableView<Entry> {
    public void initialize(WalletUtxosEntry rootEntry) {
        getStyleClass().add("utxos-treetable");

        updateAll(rootEntry);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> dateCol = new TreeTableColumn<>("Date");
        dateCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        dateCol.setCellFactory(p -> new DateCell());
        dateCol.setSortable(true);
        getColumns().add(dateCol);

        TreeTableColumn<Entry, Entry> outputCol = new TreeTableColumn<>("Output");
        outputCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        outputCol.setCellFactory(p -> new EntryCell());
        outputCol.setSortable(true);
        outputCol.setComparator((o1, o2) -> {
            UtxoEntry entry1 = (UtxoEntry)o1;
            UtxoEntry entry2 = (UtxoEntry)o2;
            return entry1.getDescription().compareTo(entry2.getDescription());
        });
        getColumns().add(outputCol);

        TreeTableColumn<Entry, Entry> addressCol = new TreeTableColumn<>("Address");
        addressCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        addressCol.setCellFactory(p -> new AddressCell());
        addressCol.setSortable(true);
        addressCol.setComparator((o1, o2) -> {
            UtxoEntry entry1 = (UtxoEntry)o1;
            UtxoEntry entry2 = (UtxoEntry)o2;
            return entry1.getAddress().toString().compareTo(entry2.getAddress().toString());
        });
        getColumns().add(addressCol);

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(true);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new AmountCell());
        amountCol.setSortable(true);
        getColumns().add(amountCol);
        setTreeColumn(amountCol);

        setPlaceholder(new Label("No unspent outputs"));
        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        dateCol.setSortType(TreeTableColumn.SortType.DESCENDING);
        getSortOrder().add(dateCol);
    }

    public void updateAll(WalletUtxosEntry rootEntry) {
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);

        if(getColumns().size() > 0 && getSortOrder().isEmpty()) {
            TreeTableColumn<Entry, ?> dateCol = getColumns().get(0);
            getSortOrder().add(dateCol);
            dateCol.setSortType(TreeTableColumn.SortType.DESCENDING);
        }
    }

    public void updateHistory(List<WalletNode> updatedNodes) {
        //Recalculate from scratch and update accordingly
        WalletUtxosEntry rootEntry = (WalletUtxosEntry)getRoot().getValue();
        rootEntry.updateUtxos();
        sort();
    }
}
