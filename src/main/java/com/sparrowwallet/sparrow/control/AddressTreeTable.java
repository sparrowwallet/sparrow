package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ReceiveActionEvent;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.util.converter.DefaultStringConverter;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.lang.reflect.Field;
import java.util.Locale;

public class AddressTreeTable extends TreeTableView<AddressTreeTable.Data> {
    public void initialize(Wallet.Node rootNode) {
        getStyleClass().add("address-treetable");

        String address = null;
        Data rootData = new Data(rootNode);
        TreeItem<Data> rootItem = new TreeItem<>(rootData);
        for(Wallet.Node childNode : rootNode.getChildren()) {
            Data childData = new Data(childNode);
            TreeItem<Data> childItem = new TreeItem<>(childData);
            rootItem.getChildren().add(childItem);
            address = childNode.getAddress().toString();
        }

        rootItem.setExpanded(true);
        setRoot(rootItem);
        setShowRoot(false);

        TreeTableColumn<Data, Data> addressCol = new TreeTableColumn<>("Address / Outpoints");
        addressCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Data, Data> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue());
        });
        addressCol.setCellFactory(p -> new DataCell());
        addressCol.setSortable(false);
        getColumns().add(addressCol);

        if(address != null) {
            addressCol.setMinWidth(TextUtils.computeTextWidth(Font.font("Courier"), address, 0.0));
        }

        TreeTableColumn<Data, String> labelCol = new TreeTableColumn<>("Label");
        labelCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Data, String> param) -> {
            return param.getValue().getValue().getLabelProperty();
        });
        labelCol.setCellFactory(p -> new LabelCell());
        labelCol.setSortable(false);
        getColumns().add(labelCol);

        TreeTableColumn<Data, Long> amountCol = new TreeTableColumn<>("Amount");
        amountCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Data, Long> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getValue().getAmount());
        });
        amountCol.setCellFactory(p -> new AmountCell());
        amountCol.setSortable(false);
        getColumns().add(amountCol);

        TreeTableColumn<Data, Void> actionCol = new TreeTableColumn<>("Actions");
        actionCol.setCellFactory(p -> new ActionCell());
        actionCol.setSortable(false);
        getColumns().add(actionCol);

        setEditable(true);
        setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
    }

    public static class Data {
        private final Wallet.Node walletNode;
        private final SimpleStringProperty labelProperty;
        private final Long amount;

        public Data(Wallet.Node walletNode) {
            this.walletNode = walletNode;

            labelProperty = new SimpleStringProperty(walletNode.getLabel());
            labelProperty.addListener((observable, oldValue, newValue) -> walletNode.setLabel(newValue));

            amount = walletNode.getAmount();
        }

        public Wallet.Node getWalletNode() {
            return walletNode;
        }

        public String getLabel() {
            return labelProperty.get();
        }

        public StringProperty getLabelProperty() {
            return labelProperty;
        }

        public Long getAmount() {
            return amount;
        }
    }

    private static class DataCell extends TreeTableCell<Data, Data> {
        public DataCell() {
            super();
            getStyleClass().add("data-cell");
        }

        @Override
        protected void updateItem(Data data, boolean empty) {
            super.updateItem(data, empty);

            if(empty) {
                setText(null);
                setGraphic(null);
            } else {
                if(data.getWalletNode() != null) {
                    Address address = data.getWalletNode().getAddress();
                    setText(address.toString());
                    setContextMenu(new AddressContextMenu(address));
                } else {
                    //TODO: Add transaction outpoint
                }
            }
        }
    }

    private static class AddressContextMenu extends ContextMenu {
        public AddressContextMenu(Address address) {
            MenuItem copyAddress = new MenuItem("Copy Address");
            copyAddress.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(address.toString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            MenuItem copyHex = new MenuItem("Copy Script Output Bytes");
            copyHex.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(Utils.bytesToHex(address.getOutputScriptData()));
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyAddress, copyHex);
        }
    }

    private static class LabelCell extends TextFieldTreeTableCell<Data, String> {
        public LabelCell() {
            super(new DefaultStringConverter());
            getStyleClass().add("label-cell");
        }

        @Override
        public void updateItem(String label, boolean empty) {
            super.updateItem(label, empty);

            if(empty) {
                setText(null);
                setGraphic(null);
            } else {
                setText(label);
                setContextMenu(new LabelContextMenu(label));
            }
        }

        @Override
        public void commitEdit(String label) {
            // This block is necessary to support commit on losing focus, because
            // the baked-in mechanism sets our editing state to false before we can
            // intercept the loss of focus. The default commitEdit(...) method
            // simply bails if we are not editing...
            if (!isEditing() && !label.equals(getItem())) {
                TreeTableView<Data> treeTable = getTreeTableView();
                if(treeTable != null) {
                    TreeTableColumn<Data, String> column = getTableColumn();
                    TreeTableColumn.CellEditEvent<Data, String> event = new TreeTableColumn.CellEditEvent<>(
                            treeTable, new TreeTablePosition<>(treeTable, getIndex(), column),
                            TreeTableColumn.editCommitEvent(), label
                    );
                    Event.fireEvent(column, event);
                }
            }

            super.commitEdit(label);
        }

        @Override
        public void startEdit() {
            super.startEdit();

            try {
                Field f = getClass().getSuperclass().getDeclaredField("textField");
                f.setAccessible(true);
                TextField textField = (TextField)f.get(this);
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused) {
                        commitEdit(getConverter().fromString(textField.getText()));
                        setText(getConverter().fromString(textField.getText()));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class LabelContextMenu extends ContextMenu {
        public LabelContextMenu(String label) {
            MenuItem copyLabel = new MenuItem("Copy Label");
            copyLabel.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(label);
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().add(copyLabel);
        }
    }

    private static class AmountCell extends TreeTableCell<Data, Long> {
        public AmountCell() {
            super();
            getStyleClass().add("amount-cell");
        }

        @Override
        protected void updateItem(Long amount, boolean empty) {
            super.updateItem(amount, empty);

            if(empty || amount == null) {
                setText(null);
                setGraphic(null);
            } else {
                String satsValue = String.format(Locale.ENGLISH, "%,d", amount) + " sats";
                String btcValue = CoinLabel.BTC_FORMAT.format(amount.doubleValue() / Transaction.SATOSHIS_PER_BITCOIN) + " BTC";
                Tooltip tooltip = new Tooltip();
                if(amount > CoinLabel.MAX_SATS_SHOWN) {
                    tooltip.setText(satsValue);
                    setText(btcValue);
                } else {
                    tooltip.setText(btcValue);
                    setText(satsValue);
                }
                setTooltip(tooltip);
            }
        }
    }

    private static class ActionCell extends TreeTableCell<Data, Void> {
        private final HBox actionBox;

        public ActionCell() {
            super();
            getStyleClass().add("action-cell");

            actionBox = new HBox();
            actionBox.setSpacing(8);
            actionBox.setAlignment(Pos.CENTER);

            Button receiveButton = new Button("");
            Glyph receiveGlyph = new Glyph("FontAwesome", FontAwesome.Glyph.ARROW_DOWN);
            receiveGlyph.setFontSize(12);
            receiveButton.setGraphic(receiveGlyph);
            receiveButton.setOnAction(event -> {
                EventManager.get().post(new ReceiveActionEvent(getTreeTableView().getTreeItem(getIndex()).getValue().getWalletNode()));
            });

            actionBox.getChildren().add(receiveButton);
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
            } else {
                setGraphic(actionBox);
            }
        }
    }
}
