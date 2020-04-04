package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.TwoDimensional;

import java.time.Duration;
import java.util.List;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;

public abstract class TransactionFormController {
    protected void addPieData(PieChart pie, List<TransactionOutput> outputs) {
        ObservableList<PieChart.Data> outputsPieData = FXCollections.observableArrayList();

        long totalAmt = 0;
        for(TransactionOutput output : outputs) {
            String name = "Unknown";
            try {
                Address[] addresses = output.getScript().getToAddresses();
                if(addresses.length == 1) {
                    name = addresses[0].getAddress();
                } else {
                    name = "[" + addresses[0].getAddress() + ",...]";
                }
            } catch(NonStandardScriptException e) {
                //ignore
            }

            totalAmt += output.getValue();
            outputsPieData.add(new PieChart.Data(name, output.getValue()));
        }

        pie.setData(outputsPieData);

        final double totalSum = totalAmt;
        pie.getData().forEach(data -> {
            Tooltip tooltip = new Tooltip();
            double percent = 100.0 * (data.getPieValue() / totalSum);
            tooltip.setText(String.format("%.1f", percent) + "%");
            Tooltip.install(data.getNode(), tooltip);
            data.pieValueProperty().addListener((observable, oldValue, newValue) -> tooltip.setText(newValue + "%"));
        });
    }

    protected void appendScript(CodeArea codeArea, Script script) {
        appendScript(codeArea, script, null, null);
    }

    protected void appendScript(CodeArea codeArea, Script script, Script redeemScript, Script witnessScript) {
        if(ScriptPattern.isP2WPKH(script)) {
            codeArea.append(script.getChunks().get(0).toString(), "script-opcode");
            codeArea.append(" ", "");
            codeArea.append("<wpkh>", "script-hash");
        } else if(ScriptPattern.isP2WSH(script)) {
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
                } else if(chunk.isScript()) {
                    Script nestedScript = new Script(chunk.getData());
                    if (nestedScript.equals(redeemScript)) {
                        codeArea.append("<RedeemScript>", "script-redeem");
                    } else if (nestedScript.equals(witnessScript)) {
                        codeArea.append("<WitnessScript>", "script-redeem");
                    } else {
                        codeArea.append("(", "script-nest");
                        appendScript(codeArea, nestedScript);
                        codeArea.append(")", "script-nest");
                    }
                } else if(chunk.isPubKey()) {
                    codeArea.append("<pubkey" + pubKeyCount++ + ">", "script-pubkey");
                } else {
                    System.out.println(chunk.isOpCode() + " " + chunk.opcode);
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
        ScriptContextMenu contextMenu = new ScriptContextMenu();
        area.setContextMenu(contextMenu);

        Popup popup = new Popup();
        Label popupMsg = new Label();
        popupMsg.setStyle("-fx-background-color: #696c77; -fx-text-fill: white; -fx-padding: 5;");
        popup.getContent().add(popupMsg);

        area.setMouseOverTextDelay(Duration.ofMillis(150));
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, e -> {
            TwoDimensional.Position position = area.getParagraph(0).getStyleSpans().offsetToPosition(e.getCharacterIndex(), Backward);
            if(position.getMajor() % 2 == 0) {
                ScriptChunk hoverChunk = script.getChunks().get(position.getMajor()/2);
                if(!hoverChunk.isOpCode()) {
                    contextMenu.setHoverChunk(hoverChunk);
                    Point2D pos = e.getScreenPosition();
                    popupMsg.setText(hoverChunk.toString());
                    popup.show(area, pos.getX(), pos.getY() + 10);
                } else {
                    contextMenu.setHoverChunk(null);
                }
            } else {
                contextMenu.setHoverChunk(null);
            }
        });
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> {
            popup.hide();
        });
    }
}
