package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.beans.NamedArg;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.util.Iterator;
import java.util.Map;

public class BlockTargetFeeRatesChart extends LineChart<String, Number> {
    private XYChart.Series<String, Number> feeRateSeries;
    private Integer selectedTargetBlocks;

    public BlockTargetFeeRatesChart(@NamedArg("xAxis") Axis<String> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
    }

    public void initialize() {
        feeRateSeries = new XYChart.Series<>();
        getData().add(feeRateSeries);
    }

    public void update(Map<Integer, Double> targetBlocksFeeRates) {
        feeRateSeries.getData().clear();

        for(Iterator<Integer> targetBlocksIter = targetBlocksFeeRates.keySet().iterator(); targetBlocksIter.hasNext(); ) {
            Integer targetBlocks = targetBlocksIter.next();
            if(AppServices.TARGET_BLOCKS_RANGE.contains(targetBlocks)) {
                String category = targetBlocks + (targetBlocksIter.hasNext() ? "" : "+");
                XYChart.Data<String, Number> data = new XYChart.Data<>(category, targetBlocksFeeRates.get(targetBlocks));
                feeRateSeries.getData().add(data);
            }
        }

        if(selectedTargetBlocks != null) {
            select(selectedTargetBlocks);
        }
    }

    public void select(Integer targetBlocks) {
        Node selectedSymbol = lookup(".chart-line-symbol.selected");
        if(selectedSymbol != null) {
            selectedSymbol.getStyleClass().remove("selected");
        }

        for(int i = 0; i < feeRateSeries.getData().size(); i++) {
            XYChart.Data<String, Number> data = feeRateSeries.getData().get(i);
            Node symbol = lookup(".chart-line-symbol.data" + i);
            if(symbol != null) {
                if(data.getXValue().replace("+", "").equals(targetBlocks.toString())) {
                    symbol.getStyleClass().add("selected");
                    selectedTargetBlocks = targetBlocks;
                }
            }
        }
    }
}
