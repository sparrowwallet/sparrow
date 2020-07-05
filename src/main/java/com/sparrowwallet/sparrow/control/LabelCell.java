package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.Entry;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.converter.DefaultStringConverter;

import java.lang.reflect.Field;

class LabelCell extends TextFieldTreeTableCell<Entry, String> {
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
            EntryCell.applyRowStyles(this, getTreeTableView().getTreeItem(getIndex()).getValue());

            setText(label);
            setContextMenu(new LabelContextMenu(label));
        }
    }

    @Override
    public void commitEdit(String label) {
        if(label != null) {
            label = label.trim();
        }

        // This block is necessary to support commit on losing focus, because
        // the baked-in mechanism sets our editing state to false before we can
        // intercept the loss of focus. The default commitEdit(...) method
        // simply bails if we are not editing...
        if (!isEditing() && !label.equals(getItem())) {
            TreeTableView<Entry> treeTable = getTreeTableView();
            if(treeTable != null) {
                TreeTableColumn<Entry, String> column = getTableColumn();
                TreeTableColumn.CellEditEvent<Entry, String> event = new TreeTableColumn.CellEditEvent<>(
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
}
