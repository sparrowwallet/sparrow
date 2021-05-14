package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ReceiveActionEvent;
import com.sparrowwallet.sparrow.event.ReceiveToEvent;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class AddressTreeTable extends CoinTreeTable {
    public void initialize(NodeEntry rootEntry) {
        getStyleClass().add("address-treetable");
        setBitcoinUnit(rootEntry.getWallet());

        String address = rootEntry.getAddress().toString();
        updateAll(rootEntry);
        setShowRoot(false);

        TreeTableColumn<Entry, Entry> addressCol = new TreeTableColumn<>("Address / Outpoints");
        addressCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Entry> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        addressCol.setCellFactory(p -> new EntryCell());
        addressCol.setSortable(false);
        getColumns().add(addressCol);

        if(address != null) {
            addressCol.setMinWidth(TextUtils.computeTextWidth(AppServices.getMonospaceFont(), address, 0.0));
        }

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(false);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Number> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Number> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new CoinCell());
        amountCol.setSortable(false);
        getColumns().add(amountCol);

        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

        addressCol.setSortType(TreeTableColumn.SortType.ASCENDING);
        getSortOrder().add(addressCol);

        Integer highestUsedIndex = rootEntry.getNode().getHighestUsedIndex();
        if(highestUsedIndex != null) {
            OptionalInt tableIndex = rootEntry.getChildren().stream().filter(childEntry -> ((NodeEntry)childEntry).getNode().getIndex() == highestUsedIndex + 1).mapToInt(childEntry -> rootEntry.getChildren().indexOf(childEntry)).findFirst();
            if(tableIndex.isPresent() && tableIndex.getAsInt() > 5) {
                scrollTo(tableIndex.getAsInt());
            }
        }

        setOnMouseClicked(mouseEvent -> {
            if(mouseEvent.getButton().equals(MouseButton.PRIMARY)){
                if(mouseEvent.getClickCount() == 2) {
                    TreeItem<Entry> treeItem = getSelectionModel().getSelectedItem();
                    if(treeItem != null && treeItem.getChildren().isEmpty()) {
                        Entry entry = getSelectionModel().getSelectedItem().getValue();
                        if(entry instanceof NodeEntry) {
                            NodeEntry nodeEntry = (NodeEntry)entry;
                            EventManager.get().post(new ReceiveActionEvent(nodeEntry));
                            Platform.runLater(() -> EventManager.get().post(new ReceiveToEvent(nodeEntry)));
                        }
                    }
                }
            }
        });

        rootEntry.getChildren().addListener((ListChangeListener<Entry>) c -> {
            this.refresh();
        });
    }

    public void updateAll(NodeEntry rootEntry) {
        setBitcoinUnit(rootEntry.getWallet());

        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);

        if(getColumns().size() > 0 && getSortOrder().isEmpty()) {
            TreeTableColumn<Entry, ?> addressCol = getColumns().get(0);
            getSortOrder().add(addressCol);
            addressCol.setSortType(TreeTableColumn.SortType.ASCENDING);
        }
    }

    public void updateHistory(List<WalletNode> updatedNodes) {
        //We only ever add child nodes - never remove in order to keep a full sequence
        NodeEntry rootEntry = (NodeEntry)getRoot().getValue();

        for(WalletNode updatedNode : updatedNodes) {
            Optional<Entry> optEntry = rootEntry.getChildren().stream().filter(childEntry -> ((NodeEntry)childEntry).getNode().equals(updatedNode)).findFirst();
            if(optEntry.isPresent()) {
                NodeEntry existingEntry = (NodeEntry)optEntry.get();
                existingEntry.refreshChildren();
            } else {
                NodeEntry nodeEntry = new NodeEntry(rootEntry.getWallet(), updatedNode);
                rootEntry.getChildren().add(nodeEntry);
            }
        }

        refresh();
    }

    public void updateLabel(Entry entry) {
        Entry rootEntry = getRoot().getValue();
        rootEntry.updateLabel(entry);
    }
}
