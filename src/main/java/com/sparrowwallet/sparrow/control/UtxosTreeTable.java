package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.util.Comparator;

public class UtxosTreeTable extends CoinTreeTable {
    public void initialize(WalletUtxosEntry rootEntry) {
        getStyleClass().add("utxos-treetable");
        setUnitFormat(rootEntry.getWallet());

        updateAll(rootEntry);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> dateCol = new TreeTableColumn<>("Date");
        dateCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        dateCol.setCellFactory(p -> new DateCell());
        dateCol.setSortable(true);
        dateCol.setComparator(dateCol.getComparator().reversed());
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
            int hashCompare = entry1.getHashIndex().getHash().toString().compareTo(entry2.getHashIndex().getHash().toString());
            if(hashCompare != 0) {
                return hashCompare;
            }

            return (int)(entry1.getHashIndex().getIndex() - entry2.getHashIndex().getIndex());
        });
        getColumns().add(outputCol);

        if(rootEntry.getWallet().isWhirlpoolMixWallet()) {
            TreeTableColumn<Entry, UtxoEntry.MixStatus> mixStatusCol = new TreeTableColumn<>("Mixes");
            mixStatusCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, UtxoEntry.MixStatus> param) -> {
                return ((UtxoEntry)param.getValue().getValue()).mixStatusProperty();
            });
            mixStatusCol.setCellFactory(p -> new MixStatusCell());
            mixStatusCol.setSortable(true);
            mixStatusCol.setComparator(Comparator.comparingInt(UtxoEntry.MixStatus::getMixesDone));
            getColumns().add(mixStatusCol);
        } else {
            TreeTableColumn<Entry, UtxoEntry.AddressStatus> addressCol = new TreeTableColumn<>("Address");
            addressCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, UtxoEntry.AddressStatus> param) -> {
                return ((UtxoEntry)param.getValue().getValue()).addressStatusProperty();
            });
            addressCol.setCellFactory(p -> new AddressCell());
            addressCol.setSortable(true);
            addressCol.setComparator(Comparator.comparing(o -> o.getAddress().toString()));
            getColumns().add(addressCol);
        }

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
        amountCol.setCellFactory(p -> new CoinCell());
        amountCol.setSortable(true);
        getColumns().add(amountCol);
        setTreeColumn(amountCol);

        setPlaceholder(getDefaultPlaceholder(rootEntry.getWallet()));
        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        setSortColumn(getColumns().size() - 1, TreeTableColumn.SortType.DESCENDING);

        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void updateAll(WalletUtxosEntry rootEntry) {
        setUnitFormat(rootEntry.getWallet());

        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);

        setSortColumn(getColumns().size() - 1, TreeTableColumn.SortType.DESCENDING);
    }

    public void updateHistory() {
        //Utxo entries should have already been updated, so only a resort required
        if(!getRoot().getChildren().isEmpty()) {
            sort();
            setSortColumn(getColumns().size() - 1, TreeTableColumn.SortType.DESCENDING);
        }
    }

    public void updateLabel(Entry entry) {
        Entry rootEntry = getRoot().getValue();
        rootEntry.updateLabel(entry);
    }
}
