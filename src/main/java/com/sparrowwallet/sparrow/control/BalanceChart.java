package com.sparrowwallet.sparrow.control;

import com.google.common.collect.Lists;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.TransactionEntry;
import com.sparrowwallet.sparrow.wallet.WalletTransactionsEntry;
import javafx.beans.NamedArg;
import javafx.scene.Node;
import javafx.scene.chart.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BalanceChart extends LineChart<Number, Number> {
    private static final int MAX_VALUES = 500;

    private XYChart.Series<Number, Number> balanceSeries;

    private TransactionEntry selectedEntry;

    public BalanceChart(@NamedArg("xAxis") Axis<Number> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
    }

    public void initialize(WalletTransactionsEntry walletTransactionsEntry) {
        managedProperty().bind(visibleProperty());
        balanceSeries = new XYChart.Series<>();
        getData().add(balanceSeries);
        update(walletTransactionsEntry);

        setUnitFormat(walletTransactionsEntry.getWallet(), Config.get().getUnitFormat(), Config.get().getBitcoinUnit());
    }

    public void update(WalletTransactionsEntry walletTransactionsEntry) {
        setVisible(!walletTransactionsEntry.getChildren().isEmpty());
        balanceSeries.getData().clear();

        List<Data<Number, Number>> balanceDataList = getTransactionEntries(walletTransactionsEntry)
                .map(entry -> (TransactionEntry)entry)
                .filter(txEntry -> txEntry.getBlockTransaction().getHeight() > 0)
                .map(txEntry -> new XYChart.Data<>((Number)txEntry.getBlockTransaction().getDate().getTime(), (Number)txEntry.getBalance(), txEntry))
                .collect(Collectors.toList());

        int size = balanceDataList.size() * 2;
        for(int i = 0; i < size; i+= 2) {
            Data<Number, Number> data = balanceDataList.get(i);

            if(i + 1 < balanceDataList.size()) {
                Data<Number, Number> nextData = balanceDataList.get(i + 1);
                Data<Number, Number> interstitialData = new Data<>(nextData.getXValue(), data.getYValue(), null);
                balanceDataList.add(i + 1, interstitialData);
            } else {
                Date now = new Date();
                Data<Number, Number> interstitialData = new Data<>(now.getTime(), data.getYValue(), null);
                balanceDataList.add(interstitialData);
            }
        }

        if(!balanceDataList.isEmpty()) {
            long min = balanceDataList.stream().map(data -> data.getXValue().longValue()).min(Long::compare).get();
            long max = balanceDataList.stream().map(data -> data.getXValue().longValue()).max(Long::compare).get();

            DateAxisFormatter dateAxisFormatter = new DateAxisFormatter(max - min);
            NumberAxis xAxis = (NumberAxis)getXAxis();
            xAxis.setTickLabelFormatter(dateAxisFormatter);
        }

        balanceSeries.getData().addAll(balanceDataList);

        if(selectedEntry != null) {
            select(selectedEntry);
        }
    }

    private Stream<Entry> getTransactionEntries(WalletTransactionsEntry walletTransactionsEntry) {
        int total = walletTransactionsEntry.getChildren().size();
        if(walletTransactionsEntry.getChildren().size() <= MAX_VALUES) {
            return walletTransactionsEntry.getChildren().stream();
        }

        int bucketSize = total / MAX_VALUES;
        List<List<Entry>> buckets = Lists.partition(walletTransactionsEntry.getChildren(), bucketSize);
        List<Entry> reducedEntries = new ArrayList<>(MAX_VALUES);
        for(List<Entry> bucket : buckets) {
            long max = bucket.stream().mapToLong(entry -> Math.abs(entry.getValue())).max().orElse(0);
            Entry bucketEntry = bucket.stream().filter(entry -> entry.getValue() == max || entry.getValue() == -max).findFirst().orElseThrow();
            reducedEntries.add(bucketEntry);
        }

        return reducedEntries.stream();
    }

    public void select(TransactionEntry transactionEntry) {
        Set<Node> selectedSymbols = lookupAll(".chart-line-symbol.selected");
        for(Node selectedSymbol : selectedSymbols) {
            selectedSymbol.getStyleClass().remove("selected");
        }

        for(int i = 0; i < balanceSeries.getData().size(); i++) {
            XYChart.Data<Number, Number> data = balanceSeries.getData().get(i);
            if(transactionEntry.getBlockTransaction().getDate() != null && data.getXValue().equals(transactionEntry.getBlockTransaction().getDate().getTime()) && data.getExtraValue() != null) {
                Node symbol = lookup(".chart-line-symbol.data" + i);
                if(symbol != null) {
                    symbol.getStyleClass().add("selected");
                    selectedEntry = transactionEntry;
                }
            }
        }
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
