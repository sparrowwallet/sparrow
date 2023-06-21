package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.Theme;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.MempoolRateSize;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.glyphfont.Glyph;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class MempoolSizeFeeRatesChart extends StackedAreaChart<String, Number> {
    private static final DateFormat dateFormatter = new SimpleDateFormat("HH:mm");
    public static final int DEFAULT_MAX_PERIOD_HOURS = 2;
    private static final double Y_VALUE_BREAK_MVB = 3.0;
    private static final List<Integer> FEE_RATES_INTERVALS = List.of(1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 50, 60, 70, 80, 90, 100, 125, 150, 175, 200, 250, 300, 350, 400, 500, 600, 700, 800);

    private int maxPeriodHours = DEFAULT_MAX_PERIOD_HOURS;
    private Tooltip tooltip;

    private MempoolSizeFeeRatesChart expandedChart;
    private final EventHandler<MouseEvent> expandedChartHandler = new EventHandler<>() {
        @Override
        public void handle(MouseEvent event) {
            if(!event.isConsumed() && event.getButton() != MouseButton.SECONDARY) {
                Stage stage = new Stage(StageStyle.UNDECORATED);
                stage.setTitle("Mempool by vBytes");
                stage.initOwner(MempoolSizeFeeRatesChart.this.getScene().getWindow());
                stage.initModality(Modality.WINDOW_MODAL);
                stage.setResizable(false);

                StackPane scenePane = new StackPane();
                if(org.controlsfx.tools.Platform.getCurrent() == org.controlsfx.tools.Platform.WINDOWS) {
                    scenePane.setBorder(new Border(new BorderStroke(Color.DARKGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
                }

                scenePane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
                if(Config.get().getTheme() == Theme.DARK) {
                    scenePane.getStylesheets().add(AppServices.class.getResource("darktheme.css").toExternalForm());
                }
                scenePane.getStylesheets().add(AppServices.class.getResource("wallet/wallet.css").toExternalForm());
                scenePane.getStylesheets().add(AppServices.class.getResource("wallet/send.css").toExternalForm());

                VBox vBox = new VBox(20);
                vBox.setPadding(new Insets(20, 20, 20, 20));

                expandedChart = new MempoolSizeFeeRatesChart();
                expandedChart.initialize();
                expandedChart.getStyleClass().add("vsizeChart");
                expandedChart.update(AppServices.getMempoolHistogram());
                expandedChart.setLegendVisible(false);
                expandedChart.setAnimated(false);
                expandedChart.setPrefWidth(700);

                HBox buttonBox = new HBox();
                buttonBox.setAlignment(Pos.CENTER);

                ToggleGroup periodGroup = new ToggleGroup();
                ToggleButton period2 = new ToggleButton("2H");
                ToggleButton period24 = new ToggleButton("24H");
                SegmentedButton periodButtons = new SegmentedButton(period2, period24);
                periodButtons.setToggleGroup(periodGroup);
                periodGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
                    expandedChart.maxPeriodHours = (newValue == period2 ? 2 : 24);
                    expandedChart.update(AppServices.getMempoolHistogram());
                });

                Optional<Date> optEarliest = AppServices.getMempoolHistogram().keySet().stream().findFirst();
                period24.setDisable(optEarliest.isEmpty() || optEarliest.get().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isAfter(LocalDateTime.now().minusHours(2)));

                Region region = new Region();
                HBox.setHgrow(region, Priority.SOMETIMES);

                Button button = new Button("Close");
                button.setOnAction(e -> {
                    stage.close();
                });
                buttonBox.getChildren().addAll(periodButtons, region, button);
                vBox.getChildren().addAll(expandedChart, buttonBox);
                scenePane.getChildren().add(vBox);

                Scene scene = new Scene(scenePane);
                AppServices.onEscapePressed(scene, stage::close);
                AppServices.setStageIcon(stage);
                stage.setScene(scene);
                stage.setOnShowing(e -> {
                    AppServices.moveToActiveWindowScreen(stage, 800, 460);
                });
                stage.setOnHidden(e -> {
                    expandedChart = null;
                });
                stage.show();
            }
        }
    };

    public MempoolSizeFeeRatesChart() {
        super(new CategoryAxis(), new NumberAxis());
    }

    public MempoolSizeFeeRatesChart(@NamedArg("xAxis") Axis<String> xAxis, @NamedArg("yAxis") Axis<Number> yAxis) {
        super(xAxis, yAxis);
        setOnMouseClicked(expandedChartHandler);
    }

    public void initialize() {
        getStyleClass().add("vsizeChart");
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

        for(int i = 0; i < FEE_RATES_INTERVALS.size(); i++) {
            int feeRate = FEE_RATES_INTERVALS.get(i);
            int nextFeeRate = (i == FEE_RATES_INTERVALS.size() - 1 ? Integer.MAX_VALUE : FEE_RATES_INTERVALS.get(i+1));
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(feeRate + "-" + (nextFeeRate == Integer.MAX_VALUE ? 900 : nextFeeRate));
            long seriesTotalVSize = 0;

            for(Date date : periodRateSizes.keySet()) {
                Set<MempoolRateSize> rateSizes = periodRateSizes.get(date);
                long totalVSize = 0;
                for(MempoolRateSize rateSize : rateSizes) {
                    if(rateSize.getFee() >= feeRate && rateSize.getFee() < nextFeeRate) {
                        totalVSize += rateSize.getVSize();
                    }
                }

                series.getData().add(new XYChart.Data<>(categories.get(date), totalVSize));
                seriesTotalVSize += totalVSize;
            }

            if(seriesTotalVSize > 0) {
                getData().add(series);
            }
        }

        for(int i = 0; i < getData().size(); i++) {
            Series<String, Number> series = getData().get(i);
            Set<Node> nodes = lookupAll(".series" + i);
            for(Node node : nodes) {
                if(node.getStyleClass().contains("chart-series-area-line")) {
                    node.setStyle("-fx-stroke: VSIZE" + series.getName() + "_COLOR; -fx-opacity: 0.2;");
                } else {
                    node.setStyle("-fx-fill: VSIZE" + series.getName() + "_COLOR; -fx-opacity: 0.5;");
                }
                node.getStyleClass().remove("default-color" + i);
            }
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

        if(expandedChart != null) {
            expandedChart.update(mempoolRateSizes);
        }
    }

    private Map<Date, Set<MempoolRateSize>> getPeriodRateSizes(Map<Date, Set<MempoolRateSize>> mempoolRateSizes) {
        if(mempoolRateSizes.size() == 1) {
            return mempoolRateSizes;
        }

        LocalDateTime period = LocalDateTime.now().minusHours(maxPeriodHours);
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
                            Label label = new Label(series.getName() + " sats/vB: " + amount);
                            Glyph circle = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CIRCLE);
                            circle.setStyle("-fx-text-fill: VSIZE" + series.getName() + "_COLOR; -fx-opacity: 0.7;");
                            label.setGraphic(circle);
                            getChildren().add(label);
                        }
                    }
                }
            }
        }
    }
}
