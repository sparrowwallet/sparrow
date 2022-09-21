package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.wallet.WalletUtxosEntry;
import javafx.beans.NamedArg;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class UtxosChart extends BarChart<String, Number> {
    private static final int MAX_BARS = 10;
    private static final String OTHER_CATEGORY = "Other";
    private static final int TOOLTIP_SHOW_DELAY = 50;

    private List<Entry> selectedEntries;
    private int totalUtxos;

    private XYChart.Series<String, Number> utxoSeries;

    public UtxosChart(@NamedArg("xAxis") Axis<String> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
    }

    public void initialize(WalletUtxosEntry walletUtxosEntry) {
        utxoSeries = new XYChart.Series<>();
        getData().add(utxoSeries);
        update(walletUtxosEntry);

        setUnitFormat(walletUtxosEntry.getWallet(), Config.get().getUnitFormat(), Config.get().getBitcoinUnit());
    }

    public void update(WalletUtxosEntry walletUtxosEntry) {
        List<Data<String, Number>> utxoDataList = walletUtxosEntry.getChildren().stream()
                .map(entry -> new XYChart.Data<>(getCategoryName(entry), (Number)entry.getValue(), entry))
                .sorted((o1, o2) -> Long.compare(o2.getYValue().longValue(), o1.getYValue().longValue()))
                .collect(Collectors.toList());

        totalUtxos = utxoDataList.size();
        if(utxoDataList.size() > MAX_BARS) {
            List<Data<String, Number>> otherDataList = utxoDataList.subList(MAX_BARS - 1, utxoDataList.size());
            List<Entry> otherEntries = otherDataList.stream().map(data -> (Entry)data.getExtraValue()).collect(Collectors.toList());
            Long otherTotal = otherDataList.stream().mapToLong(data -> data.getYValue().longValue()).sum();
            utxoDataList = utxoDataList.subList(0, MAX_BARS - 1);
            utxoDataList.add(new XYChart.Data<>(OTHER_CATEGORY, otherTotal, otherEntries));
        }

        for(int i = 0; i < utxoDataList.size(); i++) {
            XYChart.Data<String, Number> newData = utxoDataList.get(i);
            newData.setXValue((i+1) + ". " + newData.getXValue());
            if(i < utxoSeries.getData().size()) {
                XYChart.Data<String, Number> existingData = utxoSeries.getData().get(i);
                if(!newData.getXValue().equals(existingData.getXValue()) || !newData.getYValue().equals(existingData.getYValue()) || (newData.getExtraValue() instanceof Entry && !newData.getExtraValue().equals(existingData.getExtraValue()))) {
                    utxoSeries.getData().set(i, newData);
                    installTooltip(newData);
                }
            } else {
                utxoSeries.getData().add(newData);
                installTooltip(newData);
            }
        }

        if(utxoSeries.getData().size() > utxoDataList.size()) {
            utxoSeries.getData().remove(utxoDataList.size(), utxoSeries.getData().size());
        }

        if(selectedEntries != null) {
            select(selectedEntries);
        }
    }

    private String getCategoryName(Entry entry) {
        if(entry.getLabel() != null && !entry.getLabel().isEmpty()) {
            return entry.getLabel() + "\n" + ((UtxoEntry)entry).getDescription();
        }

        return ((UtxoEntry)entry).getDescription();
    }

    private void installTooltip(XYChart.Data<String, Number> item) {
        Tooltip.uninstall(item.getNode(), null);

        String satsValue = String.format(Locale.ENGLISH, "%,d", item.getYValue());
        Tooltip tooltip = new Tooltip(item.getXValue() + "\n" + satsValue + " sats");
        tooltip.setShowDelay(Duration.millis(TOOLTIP_SHOW_DELAY));
        Tooltip.install(item.getNode(), tooltip);
    }

    public void select(List<Entry> entries) {
        Set<Node> selectedBars = lookupAll(".chart-bar.selected");
        for(Node selectedBar : selectedBars) {
            selectedBar.getStyleClass().remove("selected");
        }

        for(int i = 0; i < utxoSeries.getData().size(); i++) {
            XYChart.Data<String, Number> data = utxoSeries.getData().get(i);

            if((data.getExtraValue() instanceof Entry && entries.contains((Entry)data.getExtraValue())) ||
                    (data.getExtraValue() instanceof List && entries.containsAll((List)data.getExtraValue()))) {
                Node bar = lookup(".data" + i);
                if(bar != null) {
                    bar.getStyleClass().add("selected");
                }
            }
        }

        this.selectedEntries = entries;
    }

    public void setUnitFormat(Wallet wallet, UnitFormat format, BitcoinUnit unit) {
        if(format == null) {
            format = UnitFormat.DOT;
        }

        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = wallet.getAutoUnit();
        }

        NumberAxis yaxis = (NumberAxis)getYAxis();
        yaxis.setTickLabelFormatter(new CoinAxisFormatter(yaxis, format, unit));
    }
}
