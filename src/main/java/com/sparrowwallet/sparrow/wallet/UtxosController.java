package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.UtxosTreeTable;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.event.WalletNodesChangedEvent;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class UtxosController extends WalletFormController implements Initializable {

    @FXML
    private UtxosTreeTable utxosTable;

    @FXML
    private BarChart<String, Number> utxosChart;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        utxosTable.initialize(getWalletForm().getWalletUtxosEntry());
        initializeChart(getWalletForm().getWalletUtxosEntry());
    }

    private void initializeChart(WalletUtxosEntry walletUtxosEntry) {
        XYChart.Series<String, Number> utxoSeries = new XYChart.Series<>();

        List<XYChart.Data<String, Number>> utxoDataList = walletUtxosEntry.getChildren().stream()
                .map(UtxoData::new)
                .map(data -> new XYChart.Data<>(data.name, data.value, data.entry))
                .collect(Collectors.toList());
        utxoSeries.getData().addAll(utxoDataList);
        walletUtxosEntry.getChildren().forEach(entry -> entry.labelProperty().addListener((observable, oldValue, newValue) -> {
            Optional<XYChart.Data<String, Number>> optData = utxoSeries.getData().stream().filter(data -> data.getExtraValue().equals(entry)).findFirst();
            if(optData.isPresent()) {
                int index = utxoSeries.getData().indexOf(optData.get());
                utxoSeries.getData().set(index, new XYChart.Data<>(newValue, optData.get().getYValue(), optData.get().getExtraValue()));
            }
        }));
        utxoSeries.getData().sort((o1, o2) -> (int) (o2.getYValue().longValue() - o1.getYValue().longValue()));

        utxosChart.getData().clear();
        utxosChart.getData().add(utxoSeries);

        walletUtxosEntry.getChildren().addListener((ListChangeListener<Entry>) change -> {
            while(change.next()) {
                if(change.wasAdded()) {
                    List<XYChart.Data<String, Number>> addedList = change.getAddedSubList().stream().map(UtxoData::new).map(data -> new XYChart.Data<>(data.name, data.value, data.entry)).collect(Collectors.toList());
                    utxoSeries.getData().addAll(addedList);
                    utxoSeries.getData().sort((o1, o2) -> (int) (o2.getYValue().longValue() - o1.getYValue().longValue()));
                    change.getAddedSubList().forEach(entry -> entry.labelProperty().addListener((observable, oldValue, newValue) -> {
                        Optional<XYChart.Data<String, Number>> optData = utxoSeries.getData().stream().filter(data -> data.getExtraValue().equals(entry)).findFirst();
                        if(optData.isPresent()) {
                            int index = utxoSeries.getData().indexOf(optData.get());
                            utxoSeries.getData().set(index, new XYChart.Data<>(newValue, optData.get().getYValue(), optData.get().getExtraValue()));
                        }
                    }));
                }

                if(change.wasRemoved()) {
                    change.getRemoved().forEach(entry -> {
                        UtxoData utxoData = new UtxoData(entry);
                        Optional<XYChart.Data<String, Number>> optRemovedData = utxoSeries.getData().stream().filter(data -> data.getExtraValue().equals(utxoData.entry)).findFirst();
                        optRemovedData.ifPresent(removedData -> utxoSeries.getData().remove(removedData));
                    });
                }
            }
        });
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            utxosTable.updateAll(getWalletForm().getWalletUtxosEntry());
            initializeChart(getWalletForm().getWalletUtxosEntry());
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            utxosTable.updateHistory(event.getHistoryChangedNodes());
        }
    }

    private static class UtxoData {
        public final Entry entry;
        public final String name;
        public final Number value;

        public UtxoData(Entry entry) {
            this.entry = entry;
            this.name = entry.getLabel() != null && !entry.getLabel().isEmpty() ? entry.getLabel() : ((UtxoEntry)entry).getDescription();
            this.value = entry.getValue();
        }
    }
}
