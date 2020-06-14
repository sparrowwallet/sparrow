package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.NonStandardScriptException;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.sparrow.BaseController;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.List;

public abstract class TransactionFormController extends BaseController {
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

        addPieData(pie, outputsPieData);
    }

    protected void addCoinbasePieData(PieChart pie, long value) {
        ObservableList<PieChart.Data> outputsPieData = FXCollections.observableList(List.of(new PieChart.Data("Coinbase", value)));
        addPieData(pie, outputsPieData);
    }

    private void addPieData(PieChart pie, ObservableList<PieChart.Data> outputsPieData) {
        pie.setData(outputsPieData);
        final double totalSum = outputsPieData.stream().map(PieChart.Data::getPieValue).mapToDouble(Double::doubleValue).sum();
        pie.getData().forEach(data -> {
            Tooltip tooltip = new Tooltip();
            double percent = 100.0 * (data.getPieValue() / totalSum);
            tooltip.setText(String.format("%.1f", percent) + "%");
            Tooltip.install(data.getNode(), tooltip);
            data.pieValueProperty().addListener((observable, oldValue, newValue) -> tooltip.setText(newValue + "%"));
        });
    }

    public static class TransactionReferenceContextMenu extends ContextMenu {
        public TransactionReferenceContextMenu(String reference) {
            MenuItem referenceItem = new MenuItem("Copy Reference");
            referenceItem.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(reference);
                Clipboard.getSystemClipboard().setContent(content);
            });
            getItems().add(referenceItem);
        }
    }
}
