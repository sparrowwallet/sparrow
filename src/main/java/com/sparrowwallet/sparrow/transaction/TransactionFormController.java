package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.NonStandardScriptException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Tooltip;
import org.fxmisc.richtext.CodeArea;

import java.util.List;

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

    protected void appendScript(CodeArea codeArea, String script) {
        String[] parts = script.split(" ");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if(part.startsWith("(")) {
                codeArea.append("(", "script-nest");
                part = part.substring(1);
            }

            boolean appendCloseBracket = false;
            if(part.endsWith(")")) {
                appendCloseBracket = true;
                part = part.substring(0, part.length() - 1);
            }

            if(part.startsWith("OP")) {
                codeArea.append(part, "script-opcode");
            } else if(part.startsWith("<signature")) {
                codeArea.append(part, "script-signature");
            } else if(part.startsWith("<pubkey")) {
                codeArea.append(part, "script-pubkey");
            } else if(part.startsWith("<wpkh") || part.startsWith("<wsh")) {
                codeArea.append(part, "script-type");
            } else {
                codeArea.append(part, "script-other");
            }

            if(appendCloseBracket) {
                codeArea.append(")", "script-nest");
            }

            if(i < parts.length - 1) {
                codeArea.append(" ", "");
            }
        }
    }
}
