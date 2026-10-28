package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.NamedArg;
import javafx.collections.FXCollections;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BlockTargetFeeRatesChart extends LineChart<String, Number> {
    private XYChart.Series<String, Number> feeRateSeries;
    private Integer selectedTargetBlocks;

    public BlockTargetFeeRatesChart(@NamedArg("xAxis") Axis<String> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
        setAccessibleRole(AccessibleRole.TEXT);
        AppServices.keepChartPointsOutOfTabOrder(getPlotChildren());
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if(attribute == AccessibleAttribute.CHILDREN) {
            return FXCollections.emptyObservableList();
        }

        return super.queryAccessibleAttribute(attribute, parameters);
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

        setAccessibleText(getSummary());

        if(selectedTargetBlocks != null) {
            select(selectedTargetBlocks);
        }
    }

    private String getSummary() {
        List<XYChart.Data<String, Number>> data = feeRateSeries.getData();
        if(data.isEmpty()) {
            return "Fee rates by target blocks, no fee rates";
        }

        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        XYChart.Data<String, Number> first = data.getFirst();
        XYChart.Data<String, Number> last = data.getLast();
        return "Fee rates by target blocks, from " + format.getCurrencyFormat().format(first.getYValue()) + " sats/vB for " + first.getXValue() + " block"
                + (first.getXValue().equals("1") ? "" : "s") + " to " + format.getCurrencyFormat().format(last.getYValue()) + " sats/vB for " + last.getXValue() + " blocks";
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
