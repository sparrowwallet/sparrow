package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.NonStandardScriptException;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.BaseController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.TransactionTabData;
import com.sparrowwallet.sparrow.event.TransactionTabsClosedEvent;
import com.sparrowwallet.sparrow.io.Config;
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
    private static final int MAX_PIE_SEGMENTS = 200;

    protected abstract TransactionForm getTransactionForm();

    protected void addPieData(PieChart pie, List<TransactionOutput> outputs) {
        ObservableList<PieChart.Data> outputsPieData = FXCollections.observableArrayList();

        long totalAmt = 0;
        for(int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            String name = "#" + i;
            try {
                Address[] addresses = output.getScript().getToAddresses();
                if(addresses.length == 1) {
                    name = name + " " + addresses[0].getAddress();
                } else {
                    name = name + " [" + addresses[0].getAddress() + ",...]";
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
        if(outputsPieData.size() > MAX_PIE_SEGMENTS) {
            return;
        }

        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        pie.setData(outputsPieData);
        final double totalSum = outputsPieData.stream().map(PieChart.Data::getPieValue).mapToDouble(Double::doubleValue).sum();
        pie.getData().forEach(data -> {
            Tooltip tooltip = new Tooltip();
            double percent = 100.0 * (data.getPieValue() / totalSum);
            String satsValue = format.formatSatsValue((long)data.getPieValue()) + " sats";
            String btcValue = format.formatBtcValue((long)data.getPieValue()) + " BTC";
            tooltip.setText(data.getName() + "\n" + (Config.get().getBitcoinUnit() == BitcoinUnit.BTC ? btcValue : satsValue) + " (" + String.format("%.1f", percent) + "%)");
            Tooltip.install(data.getNode(), tooltip);
            data.pieValueProperty().addListener((observable, oldValue, newValue) -> tooltip.setText(newValue + "%"));
        });
    }

    @Subscribe
    public void transactionTabsClosed(TransactionTabsClosedEvent event) {
        for(TransactionTabData tabData : event.getClosedTransactionTabData()) {
            if(tabData.getTransactionData() == getTransactionForm().getTransactionData()) {
                EventManager.get().unregister(this);
            }
        }
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
