package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.TwoDimensional;

import java.util.OptionalInt;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;

public class ScriptContextMenu extends ContextMenu {
    private Script script;
    private MenuItem copyvalue;
    private ScriptChunk hoverChunk;

    public ScriptContextMenu(CodeArea area, Script script)
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
                TwoDimensional.Position position = area.getParagraph(0).getStyleSpans().offsetToPosition(characterIndex.getAsInt(), Backward);
                if(position.getMajor() % 2 == 0) {
                    ScriptChunk chunk = script.getChunks().get(position.getMajor() / 2);
                    if(!chunk.isOpCode()) {
                        this.hoverChunk = chunk;
                    }
                }
            }
            copyvalue.setDisable(hoverChunk == null);
        });
    }
}
