package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.sparrow.BaseController;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;

import java.util.OptionalInt;

public class ScriptContextMenu extends ContextMenu {
    private Script script;
    private MenuItem copyvalue;
    private ScriptChunk hoverChunk;

    public ScriptContextMenu(ScriptArea area, Script script)
    {
        this.script = script;

        copyvalue = new MenuItem("Copy Value");
        copyvalue.setOnAction(AE -> {
            hide();
            ClipboardContent content = new ClipboardContent();
            content.putString(hoverChunk.toString());
            Clipboard.getSystemClipboard().setContent(content);
        });

        getItems().add(copyvalue);
        this.setStyle("-fx-background-color: -fx-color; -fx-font-family: System; -fx-font-size: 1em;");

        area.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            hoverChunk = null;
            Point2D point = area.screenToLocal(event.getScreenX(), event.getScreenY());
            OptionalInt characterIndex = area.hit(point.getX(), point.getY()).getCharacterIndex();
            if(characterIndex.isPresent()) {
                ScriptChunk chunk = BaseController.getScriptChunk(area, characterIndex.getAsInt());
                if(chunk != null) {
                    this.hoverChunk = chunk;
                }
            }
            copyvalue.setDisable(hoverChunk == null);
        });
    }
}
