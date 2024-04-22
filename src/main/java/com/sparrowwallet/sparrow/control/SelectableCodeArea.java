package com.sparrowwallet.sparrow.control;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.fxmisc.richtext.CodeArea;

public class SelectableCodeArea extends CodeArea {
    public SelectableCodeArea() {
        super();

        ContextMenu contextMenu = new ContextMenu();
        MenuItem copy = new MenuItem("Copy");
        copy.setDisable(true);
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        MenuItem copyAll = new MenuItem("Copy All");
        copyAll.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        contextMenu.getItems().addAll(copy, copyAll);
        setContextMenu(contextMenu);

        selectedTextProperty().addListener((observable, oldValue, newValue) -> {
            copy.setDisable(newValue.isEmpty());
        });
    }
}
