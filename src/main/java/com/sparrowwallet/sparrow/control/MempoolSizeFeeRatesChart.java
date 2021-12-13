package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.net.MempoolRateSize;
import com.sparrowwallet.sparrow.wallet.SendController;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.glyphfont.Glyph;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class MempoolSizeFeeRatesChart extends StackedAreaChart<String, Number> {
    private static final DateFormat dateFormatter = new SimpleDateFormat("HH:mm");
    public static final int MAX_PERIOD_HOURS = 2;
    private static final double Y_VALUE_BREAK_MVB = 3.0;

    private Tooltip tooltip;

    public MempoolSizeFeeRatesChart(@NamedArg("xAxis") Axis<String> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
    }

    public void initialize() {
        setCreateSymbols(false);
        setCursor(Cursor.CROSSHAIR);
        setVerticalGridLinesVisible(false);
        tooltip = new Tooltip();
        tooltip.setShowDelay(Duration.ZERO);
        tooltip.setHideDelay(Duration.ZERO);
        tooltip.setShowDuration(Duration.INDEFINITE);
        Platform.runLater(() -> {
            Node node = this.lookup(".plot-content");
            Tooltip.install(node, tooltip);
        });
    }

    public void update(Map<Date, Set<MempoolRateSize>> mempoolRateSizes) {
        getData().clear();
        if(tooltip.isShowing()) {
            tooltip.hide();
        }

        Map<Date, Set<MempoolRateSize>> periodRateSizes = getPeriodRateSizes(mempoolRateSizes);
        Map<Date, String> categories = getCategories(periodRateSizes);

        CategoryAxis categoryAxis = (CategoryAxis)getXAxis();
        categoryAxis.setTickMarkVisible(false);
        categoryAxis.setTickLabelGap(10);
        categoryAxis.setAutoRanging(false);
        categoryAxis.setCategories(FXCollections.observableArrayList(categories.values()));
        categoryAxis.invalidateRange(new ArrayList<>(categories.values()));
        categoryAxis.setGapStartAndEnd(false);
        categoryAxis.setTickLabelRotation(0);

        NumberAxis numberAxis = (NumberAxis)getYAxis();
        this.setOnMouseMoved(mouseEvent -> {
            Point2D sceneCoords = this.localToScene(mouseEvent.getX(), mouseEvent.getY());
            String category = categoryAxis.getValueForDisplay(categoryAxis.sceneToLocal(sceneCoords).getX());
            if(category != null) {
                Optional<String> time = categories.entrySet().stream().filter(entry -> entry.getValue().equals(category)).map(entry -> dateFormatter.format(entry.getKey())).findFirst();
                time.ifPresent(s -> tooltip.setGraphic(new ChartTooltip(category, s, getData())));
            }
        });

        long previousFeeRate = 0;
        for(Long feeRate : SendController.FEE_RATES_RANGE) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(feeRate + "+ sats/vB");
            long seriesTotalVSize = 0;

            for(Date date : periodRateSizes.keySet()) {
                Set<MempoolRateSize> rateSizes = periodRateSizes.get(date);
                long totalVSize = 0;
                for(MempoolRateSize rateSize : rateSizes) {
                    if(rateSize.getFee() > previousFeeRate && rateSize.getFee() <= feeRate) {
                        totalVSize += rateSize.getVSize();
                    }
                }

                series.getData().add(new XYChart.Data<>(categories.get(date), totalVSize));
                seriesTotalVSize += totalVSize;
            }

            if(seriesTotalVSize > 0) {
                getData().add(series);
            }

            previousFeeRate = feeRate;
        }

        final double maxMvB = getMaxMvB(getData());
        numberAxis.setTickLabelFormatter(new StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                long vSizeBytes = object.longValue();
                if(maxMvB > Y_VALUE_BREAK_MVB) {
                    return (vSizeBytes / (1000 * 1000)) + " MvB";
                } else {
                    return (vSizeBytes / (1000)) + " kvB";
                }
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        });

        if(categories.keySet().iterator().hasNext()) {
            String time = categories.values().iterator().next();
            tooltip.setGraphic(new ChartTooltip(time, time, getData()));
            numberAxis.setTickLabelsVisible(true);
            numberAxis.setOpacity(1);
        } else {
            numberAxis.setTickLabelsVisible(false);
            numberAxis.setOpacity(0);
        }
    }

    private Map<Date, Set<MempoolRateSize>> getPeriodRateSizes(Map<Date, Set<MempoolRateSize>> mempoolRateSizes) {
        if(mempoolRateSizes.size() == 1) {
            return mempoolRateSizes;
        }

        LocalDateTime period = LocalDateTime.now().minusHours(MAX_PERIOD_HOURS);
        return mempoolRateSizes.entrySet().stream().filter(entry -> {
            LocalDateTime dateTime = entry.getKey().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return dateTime.isAfter(period);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (u, v) -> { throw new IllegalStateException("Duplicate dates"); },
                TreeMap::new));
    }

    private Map<Date, String> getCategories(Map<Date, Set<MempoolRateSize>> mempoolHistogram) {
        Map<Date, String> categories = new LinkedHashMap<>();

        String invisible = "" + '\ufeff';
        for(Iterator<Date> iter = mempoolHistogram.keySet().iterator(); iter.hasNext(); ) {
            Date date = iter.next();
            String label = dateFormatter.format(date);
            if(!categories.isEmpty() && iter.hasNext()) {
                label = invisible;
                invisible += '\ufeff';
            }

            categories.put(date, label);
        }

        return categories;
    }

    private static double getMaxMvB(List<Series<String, Number>> seriesList) {
        double maxTotal = 0d;
        for(Series<String, Number> series : seriesList) {
            maxTotal = Math.max(maxTotal, getMaxMvB(series));
        }

        return maxTotal;
    }

    private static double getMaxMvB(Series<String, Number> series) {
        double total = 0d;
        for(XYChart.Data<String, Number> data : series.getData()) {
            double mvb = data.getYValue().doubleValue() / (1000 * 1000);
            total += mvb;
        }

        return total;
    }

    private static class ChartTooltip extends VBox {
        public ChartTooltip(String category, String time, List<Series<String, Number>> seriesList) {
            Label title = new Label("At " + time);
            HBox titleBox = new HBox(title);
            title.setStyle("-fx-alignment: center; -fx-font-size: 12px; -fx-padding: 0 0 5 0;");
            getChildren().add(titleBox);
            double maxMvB = getMaxMvB(seriesList);

            for(int i = seriesList.size() - 1; i >= 0; i--) {
                Series<String, Number> series = seriesList.get(i);
                for(XYChart.Data<String, Number> data : series.getData()) {
                    if(data.getXValue().equals(category)) {
                        double kvb = data.getYValue().doubleValue() / 1000;
                        double mvb = kvb / 1000;
                        if(mvb >= 0.01 || (maxMvB < Y_VALUE_BREAK_MVB && mvb > 0.001)) {
                            String amount = (maxMvB < Y_VALUE_BREAK_MVB ? (int)kvb + " kvB" : String.format("%.2f", mvb) + " MvB");
                            Label label = new Label(series.getName() + ": " + amount);
                            Glyph circle = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CIRCLE);
                            if(i < 8) {
                                circle.setStyle("-fx-text-fill: CHART_COLOR_" + (i+1));
                            }
                            label.setGraphic(circle);
                            getChildren().add(label);
                        }
                    }
                }
            }
        }
    }
}
