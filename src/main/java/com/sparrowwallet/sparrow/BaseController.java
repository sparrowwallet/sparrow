package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.sparrow.transaction.ScriptContextMenu;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.TwoDimensional;

import java.time.Duration;

import static com.sparrowwallet.drongo.protocol.ScriptType.*;
import static com.sparrowwallet.drongo.protocol.ScriptType.P2WSH;
import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;

public abstract class BaseController {
    protected void appendScript(CodeArea codeArea, Script script) {
        appendScript(codeArea, script, null, null);
    }

    protected void appendScript(CodeArea codeArea, Script script, Script redeemScript, Script witnessScript) {
        if(P2PKH.isScriptType(script)) {
            codeArea.append(script.getChunks().get(0).toString(), "script-opcode");
            codeArea.append(" ", "");
            codeArea.append(script.getChunks().get(1).toString(), "script-opcode");
            codeArea.append(" ", "");
            codeArea.append("<pkh>", "script-hash");
            codeArea.append(" ", "");
            codeArea.append(script.getChunks().get(3).toString(), "script-opcode");
            codeArea.append(" ", "");
            codeArea.append(script.getChunks().get(4).toString(), "script-opcode");
        } else if(P2SH.isScriptType(script)) {
            codeArea.append(script.getChunks().get(0).toString(), "script-opcode");
            codeArea.append(" ", "");
            codeArea.append("<sh>", "script-hash");
            codeArea.append(" ", "");
            codeArea.append(script.getChunks().get(2).toString(), "script-opcode");
        } else if(P2WPKH.isScriptType(script)) {
            codeArea.append(script.getChunks().get(0).toString(), "script-opcode");
            codeArea.append(" ", "");
            codeArea.append("<wpkh>", "script-hash");
        } else if(P2WSH.isScriptType(script)) {
            codeArea.append(script.getChunks().get(0).toString(), "script-opcode");
            codeArea.append(" ", "");
            codeArea.append("<wsh>", "script-hash");
        } else {
            int signatureCount = 1;
            int pubKeyCount = 1;
            for (int i = 0; i < script.getChunks().size(); i++) {
                ScriptChunk chunk = script.getChunks().get(i);
                if(chunk.isOpCode()) {
                    codeArea.append(chunk.toString(), "script-opcode");
                } else if(chunk.isSignature()) {
                    codeArea.append("<signature" + signatureCount++ + ">", "script-signature");
                } else if(chunk.isPubKey()) {
                    codeArea.append("<pubkey" + pubKeyCount++ + ">", "script-pubkey");
                } else if(chunk.isScript()) {
                    Script nestedScript = chunk.getScript();
                    if (nestedScript.equals(redeemScript)) {
                        codeArea.append("<RedeemScript>", "script-redeem");
                    } else if (nestedScript.equals(witnessScript)) {
                        codeArea.append("<WitnessScript>", "script-redeem");
                    } else {
                        codeArea.append("(", "script-nest");
                        appendScript(codeArea, nestedScript);
                        codeArea.append(")", "script-nest");
                    }
                } else {
                    codeArea.append(chunk.toString(), "script-other");
                }

                if(i < script.getChunks().size() - 1) {
                    codeArea.append(" ", "");
                }
            }
        }

        addScriptPopup(codeArea, script);
    }

    protected void addScriptPopup(CodeArea area, Script script) {
        ScriptContextMenu contextMenu = new ScriptContextMenu(area, script);
        area.setContextMenu(contextMenu);

        Popup popup = new Popup();
        Label popupMsg = new Label();
        popupMsg.getStyleClass().add("tooltip");
        popup.getContent().add(popupMsg);

        area.setMouseOverTextDelay(Duration.ofMillis(150));
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
            TwoDimensional.Position position = area.getParagraph(0).getStyleSpans().offsetToPosition(e.getCharacterIndex(), Backward);
            if(position.getMajor() % 2 == 0) {
                ScriptChunk hoverChunk = script.getChunks().get(position.getMajor()/2);
                if(!hoverChunk.isOpCode()) {
                    Point2D pos = e.getScreenPosition();
                    popupMsg.setText(describeScriptChunk(hoverChunk));
                    popup.show(area, pos.getX(), pos.getY() + 10);
                }
            }
        });
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> {
            popup.hide();
        });
    }

    protected String describeScriptChunk(ScriptChunk chunk) {
        return chunk.toString();
    }
}
