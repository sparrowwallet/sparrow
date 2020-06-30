package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.wallet.WalletUtxosEntry;
import javafx.beans.NamedArg;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;

import java.util.List;
import java.util.stream.Collectors;

public class UtxosChart extends BarChart<String, Number> {
    private static final int MAX_BARS = 8;
    private static final String OTHER_CATEGORY = "Other";

    private Entry selectedEntry;

    private XYChart.Series<String, Number> utxoSeries;

    public UtxosChart(@NamedArg("xAxis") Axis<String> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
    }

    public void initialize(WalletUtxosEntry walletUtxosEntry) {
        utxoSeries = new XYChart.Series<>();
        getData().add(utxoSeries);
        update(walletUtxosEntry);
    }

    public void update(WalletUtxosEntry walletUtxosEntry) {
        List<Data<String, Number>> utxoDataList = walletUtxosEntry.getChildren().stream()
                .map(entry -> new XYChart.Data<>(entry.getLabel() != null && !entry.getLabel().isEmpty() ? entry.getLabel() : ((UtxoEntry)entry).getDescription(), (Number)entry.getValue(), entry))
                .sorted((o1, o2) -> (int) (o2.getYValue().longValue() - o1.getYValue().longValue()))
                .collect(Collectors.toList());

        if(utxoDataList.size() > MAX_BARS) {
            Long otherTotal = utxoDataList.subList(MAX_BARS - 1, utxoDataList.size()).stream().mapToLong(data -> data.getYValue().longValue()).sum();
            utxoDataList = utxoDataList.subList(0, MAX_BARS - 1);
            utxoDataList.add(new XYChart.Data<>(OTHER_CATEGORY, otherTotal));
        }

        for(int i = 0; i < utxoDataList.size(); i++) {
            XYChart.Data<String, Number> newData = utxoDataList.get(i);
            if(i < utxoSeries.getData().size()) {
                XYChart.Data<String, Number> existingData = utxoSeries.getData().get(i);
                if(!newData.getXValue().equals(existingData.getXValue()) || !newData.getYValue().equals(existingData.getYValue()) || (newData.getExtraValue() != null && !newData.getExtraValue().equals(existingData.getExtraValue()))) {
                    utxoSeries.getData().set(i, newData);
                }
            } else {
                utxoSeries.getData().add(newData);
            }
        }

        if(utxoSeries.getData().size() > utxoDataList.size()) {
            utxoSeries.getData().remove(utxoDataList.size() - 1, utxoSeries.getData().size());
        }

        if(selectedEntry != null) {
            select(selectedEntry);
        }
    }

    public void select(Entry entry) {
        Node selectedBar = lookup(".chart-bar.selected");
        if(selectedBar != null) {
            selectedBar.getStyleClass().remove("selected");
        }

        for(int i = 0; i < utxoSeries.getData().size(); i++) {
            XYChart.Data<String, Number> data = utxoSeries.getData().get(i);
            Node bar = lookup(".data" + i);
            if(bar != null) {
                if(data.getExtraValue() != null && data.getExtraValue().equals(entry)) {
                    bar.getStyleClass().add("selected");
                    this.selectedEntry = entry;
                }
            }
        }
    }
}
