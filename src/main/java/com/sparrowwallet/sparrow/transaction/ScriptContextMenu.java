package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.ScriptChunk;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.fxmisc.richtext.CodeArea;

public class ScriptContextMenu extends ContextMenu {
    private MenuItem copyvalue;
    private CodeArea area;
    private IndexRange range;
    private ScriptChunk hoverChunk;

    public ScriptContextMenu()
    {
        showingProperty().addListener((ob,ov,showing) -> checkMenuItems(showing));
        this.

        copyvalue = new MenuItem("Copy Value");
        copyvalue.setOnAction(AE -> {
            hide();
            ClipboardContent content = new ClipboardContent();
            content.putString(hoverChunk.toString());
            Clipboard.getSystemClipboard().setContent(content);
        });

        getItems().add(copyvalue);

        this.setStyle("-fx-background-color: -fx-color; -fx-font-family: sans-serif; -fx-font-size: 1em;");
    }

    private void checkMenuItems(boolean showing)
    {
        if(!showing) return;
        area = (CodeArea)getOwnerNode();

        range = area.getSelection();
        copyvalue.setDisable(hoverChunk == null);
    }

    public void setHoverChunk(ScriptChunk hoverChunk) {
        this.hoverChunk = hoverChunk;
    }
}
