package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ReceiveActionEvent;
import com.sparrowwallet.sparrow.event.ReceiveToEvent;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Font;

import java.util.List;
import java.util.Optional;

public class AddressTreeTable extends TreeTableView<Entry> {
    public void initialize(NodeEntry rootEntry) {
        getStyleClass().add("address-treetable");

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
            addressCol.setMinWidth(TextUtils.computeTextWidth(Font.font("Courier"), address, 0.0));
        }

        TreeTableColumn<Entry, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, String> param) -> {
            return param.getValue().getValue().labelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(false);
        getColumns().add(labelCol);

        TreeTableColumn<Entry, Long> amountCol = new TreeTableColumn<>("Value");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Entry, Long> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getValue());
        });
        amountCol.setCellFactory(p -> new AmountCell());
        amountCol.setSortable(false);
        getColumns().add(amountCol);

        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

        Integer highestUsedIndex = rootEntry.getNode().getHighestUsedIndex();
        if(highestUsedIndex != null) {
            scrollTo(highestUsedIndex);
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
    }

    public void updateAll(NodeEntry rootEntry) {
        RecursiveTreeItem<Entry> rootItem = new RecursiveTreeItem<>(rootEntry, Entry::getChildren);
        setRoot(rootItem);
        rootItem.setExpanded(true);
    }

    public void updateHistory(List<WalletNode> updatedNodes) {
        NodeEntry rootEntry = (NodeEntry)getRoot().getValue();

        for(WalletNode updatedNode : updatedNodes) {
            Optional<Entry> optEntry = rootEntry.getChildren().stream().filter(childEntry -> ((NodeEntry)childEntry).getNode().equals(updatedNode)).findFirst();
            if(optEntry.isPresent()) {
                int index = rootEntry.getChildren().indexOf(optEntry.get());
                NodeEntry nodeEntry = new NodeEntry(rootEntry.getWallet(), updatedNode);
                rootEntry.getChildren().set(index, nodeEntry);
            }
        }
    }
}
