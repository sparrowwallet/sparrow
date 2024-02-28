package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import javafx.geometry.Pos;
import org.controlsfx.control.decoration.Decorator;
import org.controlsfx.control.decoration.GraphicDecoration;
import org.fxmisc.richtext.CodeArea;

import static com.sparrowwallet.drongo.protocol.ScriptType.*;

public class ScriptArea extends CodeArea {
    private Script script;

    public void appendScript(Script script) {
        appendScript(script, null, null);
    }

    public void appendScript(Script script, Script redeemScript, Script witnessScript) {
        if(this.script == null) {
            this.script = script;
            ScriptContextMenu contextMenu = new ScriptContextMenu(this, script);
            setContextMenu(contextMenu);
        }

        if(P2PKH.isScriptType(script)) {
            append(script.getChunks().get(0).toString(), "script-opcode");
            append(" ", "");
            append(script.getChunks().get(1).toString(), "script-opcode");
            append(" ", "");
            append("<pkh>", "script-hash");
            append(" ", "");
            append(script.getChunks().get(3).toString(), "script-opcode");
            append(" ", "");
            append(script.getChunks().get(4).toString(), "script-opcode");
        } else if(P2SH.isScriptType(script)) {
            append(script.getChunks().get(0).toString(), "script-opcode");
            append(" ", "");
            append("<sh>", "script-hash");
            append(" ", "");
            append(script.getChunks().get(2).toString(), "script-opcode");
        } else if(P2WPKH.isScriptType(script)) {
            append(script.getChunks().get(0).toString(), "script-opcode");
            append(" ", "");
            append("<wpkh>", "script-hash");
        } else if(P2WSH.isScriptType(script)) {
            append(script.getChunks().get(0).toString(), "script-opcode");
            append(" ", "");
            append("<wsh>", "script-hash");
        } else {
            int signatureCount = 1;
            int pubKeyCount = 1;
            for (int i = 0; i < script.getChunks().size(); i++) {
                ScriptChunk chunk = script.getChunks().get(i);
                if(chunk.isOpCode()) {
                    append(chunk.toString(), "script-opcode");
                } else if(chunk.isSignature()) {
                    append("<signature" + signatureCount++ + ">", "script-signature");
                } else if(chunk.isPubKey()) {
                    append("<pubkey" + pubKeyCount++ + ">", "script-pubkey");
                } else if(chunk.isTaprootControlBlock()) {
                    append("<controlblock>", "script-controlblock");
                } else if(chunk.isString()) {
                    append(chunk.toString(), "script-other");
                } else if(chunk.isScript()) {
                    Script nestedScript = chunk.getScript();
                    if (nestedScript.equals(redeemScript)) {
                        append("<RedeemScript>", "script-redeem");
                    } else if(nestedScript.equals(witnessScript)) {
                        append("<WitnessScript>", "script-redeem");
                    } else {
                        append("(", "script-nest");
                        appendScript(nestedScript);
                        append(")", "script-nest");
                    }
                } else {
                    append(chunk.toString(), "script-other");
                }

                if(i < script.getChunks().size() - 1) {
                    append(" ", "");
                }
            }
        }
    }

    public void clear() {
        super.clear();
        this.script = null;
        setDisable(false);
        setContextMenu(null);
        Decorator.removeAllDecorations(this);
    }

    public void addPSBTDecoration(String description, String styleClass) {
        Decorator.addDecoration(this, new GraphicDecoration(new TextDecoration("PSBT", description, styleClass), Pos.TOP_RIGHT));
    }

    public Script getScript() {
        return script;
    }
}
