package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.BlockSummary;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.FeeRatesSource;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.scene.Group;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.girod.javafx.svgimage.SVGImage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class BlockCube extends Group {
    public static final List<Integer> MEMPOOL_FEE_RATES_INTERVALS = List.of(1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 50, 60, 70, 80, 90, 100, 125, 150, 175, 200, 250, 300, 350, 400, 500, 600, 700, 800, 900, 1000, 1200, 1400, 1600, 1800, 2000);

    public static final double CUBE_SIZE = 60;

    private final IntegerProperty weightProperty = new SimpleIntegerProperty(0);
    private final DoubleProperty medianFeeProperty = new SimpleDoubleProperty(-Double.MAX_VALUE);
    private final IntegerProperty heightProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty txCountProperty = new SimpleIntegerProperty(0);
    private final LongProperty timestampProperty = new SimpleLongProperty(System.currentTimeMillis());
    private final StringProperty elapsedProperty = new SimpleStringProperty("");
    private final BooleanProperty confirmedProperty = new SimpleBooleanProperty(false);
    private final ObjectProperty<FeeRatesSource> feeRatesSource = new SimpleObjectProperty<>(null);

    private Polygon front;
    private Rectangle unusedArea;
    private Rectangle usedArea;

    private final Text heightText = new Text();
    private final Text medianFeeText = new Text();
    private final Text unitsText = new Text();
    private final TextFlow medianFeeTextFlow = new TextFlow();
    private final Text txCountText = new Text();
    private final Text elapsedText = new Text();
    private final Group feeRateIcon = new Group();

    public BlockCube(Integer weight, Double medianFee, Integer height, Integer txCount, Long timestamp, boolean confirmed) {
        getStyleClass().addAll("block-" + Network.getCanonical().getName(), "block-cube");
        this.confirmedProperty.set(confirmed);
        this.feeRatesSource.set(Config.get().getFeeRatesSource());

        this.weightProperty.addListener((_, _, _) -> {
            if(front != null) {
                updateFill();
            }
        });
        this.medianFeeProperty.addListener((_, _, newValue) -> {
            medianFeeText.setText(newValue.doubleValue() < 0.0d ? "" : "~" + Math.round(Math.max(newValue.doubleValue(), 1.0d)));
            unitsText.setText(newValue.doubleValue() < 0.0d ? "" : " s/vb");
            double medianFeeWidth = TextUtils.computeTextWidth(medianFeeText.getFont(), medianFeeText.getText(), 0.0d);
            double unitsWidth = TextUtils.computeTextWidth(unitsText.getFont(), unitsText.getText(), 0.0d);
            medianFeeTextFlow.setTranslateX((CUBE_SIZE - (medianFeeWidth + unitsWidth)) / 2);
        });
        this.txCountProperty.addListener((_, _, newValue) -> {
            txCountText.setText(newValue.intValue() == 0 ? "" : newValue + " txes");
            txCountText.setX((CUBE_SIZE - txCountText.getLayoutBounds().getWidth()) / 2);
        });
        this.timestampProperty.addListener((_, _, newValue) -> {
            elapsedProperty.set(getElapsed(newValue.longValue()));
        });
        this.elapsedProperty.addListener((_, _, newValue) -> {
            elapsedText.setText(isConfirmed() ? newValue : "In ~10m");
            elapsedText.setX((CUBE_SIZE - elapsedText.getLayoutBounds().getWidth()) / 2);
        });
        this.heightProperty.addListener((_, _, newValue) -> {
            heightText.setText(newValue.intValue() == 0 ? "" : String.valueOf(newValue));
            heightText.setX(((CUBE_SIZE * 0.7) - heightText.getLayoutBounds().getWidth()) / 2);
        });
        this.confirmedProperty.addListener((_, _, _) -> {
            if(front != null) {
                updateFill();
            }
        });
        this.feeRatesSource.addListener((_, _, _) -> {
            if(front != null) {
                updateFill();
            }
        });
        this.medianFeeText.textProperty().addListener((_, _, _) -> {
            pulse();
        });

        if(weight != null) {
            this.weightProperty.set(weight);
        }
        if(medianFee != null) {
            this.medianFeeProperty.set(medianFee);
        }
        if(height != null) {
            this.heightProperty.set(height);
        }
        if(txCount != null) {
            this.txCountProperty.set(txCount);
        }
        if(timestamp != null) {
            this.timestampProperty.set(timestamp);
        }

        drawCube();
    }

    private void drawCube() {
        double depth = CUBE_SIZE * 0.2;
        double perspective = CUBE_SIZE * 0.04;

        front = new Polygon(0, 0, CUBE_SIZE, 0, CUBE_SIZE, CUBE_SIZE, 0, CUBE_SIZE);
        front.getStyleClass().add("block-front");
        front.setFill(null);
        unusedArea = new Rectangle(0, 0, CUBE_SIZE, CUBE_SIZE);
        unusedArea.getStyleClass().add("block-unused");
        usedArea = new Rectangle(0, 0, CUBE_SIZE, CUBE_SIZE);
        usedArea.getStyleClass().add("block-used");

        Group frontFaceGroup = new Group(front, unusedArea, usedArea);

        Polygon top = new Polygon(0, 0, CUBE_SIZE, 0, CUBE_SIZE - depth - perspective, -depth, -depth, -depth);
        top.getStyleClass().add("block-top");
        top.setStroke(null);

        Polygon left = new Polygon(0, 0, -depth, -depth, -depth, CUBE_SIZE - depth - perspective, 0, CUBE_SIZE);
        left.getStyleClass().add("block-left");
        left.setStroke(null);

        updateFill();

        heightText.getStyleClass().add("block-height");
        heightText.setFont(new Font(11));
        heightText.setX(((CUBE_SIZE * 0.7) - heightText.getLayoutBounds().getWidth()) / 2);
        heightText.setY(-24);

        medianFeeText.getStyleClass().add("block-text");
        medianFeeText.setFont(Font.font(null, FontWeight.BOLD, 11));
        unitsText.getStyleClass().add("block-text");
        unitsText.setFont(new Font(10));
        medianFeeTextFlow.getChildren().addAll(medianFeeText, unitsText);
        medianFeeTextFlow.setTranslateX((CUBE_SIZE - (medianFeeText.getLayoutBounds().getWidth() + unitsText.getLayoutBounds().getWidth())) / 2);
        medianFeeTextFlow.setTranslateY(7);

        txCountText.getStyleClass().add("block-text");
        txCountText.setFont(new Font(10));
        txCountText.setOpacity(0.7);
        txCountText.setX((CUBE_SIZE - txCountText.getLayoutBounds().getWidth()) / 2);
        txCountText.setY(34);

        feeRateIcon.setTranslateX(((CUBE_SIZE * 0.7) - 14) / 2);
        feeRateIcon.setTranslateY(-36);

        elapsedText.getStyleClass().add("block-text");
        elapsedText.setFont(new Font(10));
        elapsedText.setX((CUBE_SIZE - elapsedText.getLayoutBounds().getWidth()) / 2);
        elapsedText.setY(50);

        getChildren().addAll(frontFaceGroup, top, left, heightText, medianFeeTextFlow, txCountText, feeRateIcon, elapsedText);
    }

    private void updateFill() {
        if(isConfirmed()) {
            getStyleClass().removeAll("block-unconfirmed");
            if(!getStyleClass().contains("block-confirmed")) {
                getStyleClass().add("block-confirmed");
            }
            double startY = 1 - weightProperty.doubleValue() / (Transaction.MAX_BLOCK_SIZE_VBYTES * Transaction.WITNESS_SCALE_FACTOR);
            double startYAbsolute = startY * BlockCube.CUBE_SIZE;
            unusedArea.setHeight(startYAbsolute);
            unusedArea.setStyle(null);
            usedArea.setY(startYAbsolute);
            usedArea.setHeight(CUBE_SIZE - startYAbsolute);
            usedArea.setVisible(true);
            heightText.setVisible(true);
            feeRateIcon.getChildren().clear();
        } else {
            getStyleClass().removeAll("block-confirmed");
            if(!getStyleClass().contains("block-unconfirmed")) {
                getStyleClass().add("block-unconfirmed");
            }
            usedArea.setVisible(false);
            unusedArea.setStyle("-fx-fill: " + getFeeRateStyleName() + ";");
            heightText.setVisible(false);
            if(feeRatesSource.get() != null) {
                SVGImage svgImage = feeRatesSource.get().getSVGImage();
                if(svgImage != null) {
                    feeRateIcon.getChildren().setAll(feeRatesSource.get().getSVGImage());
                } else {
                    feeRateIcon.getChildren().clear();
                }
            }
        }
    }

    public void pulse() {
        if(isConfirmed()) {
            return;
        }

        if(unusedArea != null) {
            unusedArea.setStyle("-fx-fill: " + getFeeRateStyleName() + ";");
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(500), new KeyValue(opacityProperty(), 0.7)),
                new KeyFrame(Duration.millis(1000), new KeyValue(opacityProperty(), 1.0))
        );

        timeline.setCycleCount(1);
        timeline.play();
    }

    private static long calculateElapsedSeconds(long timestampUtc) {
        Instant timestampInstant = Instant.ofEpochMilli(timestampUtc);
        Instant nowInstant = Instant.now();
        return ChronoUnit.SECONDS.between(timestampInstant, nowInstant);
    }

    public static String getElapsed(long timestampUtc) {
        long elapsed = calculateElapsedSeconds(timestampUtc);
        if(elapsed < 60) {
            return "Just now";
        } else if(elapsed < 3600) {
            return Math.round(elapsed / 60f) + "m ago";
        } else if(elapsed < 86400) {
            return Math.round(elapsed / 3600f) + "h ago";
        } else {
            return Math.round(elapsed / 86400d) + "d ago";
        }
    }

    private String getFeeRateStyleName() {
        double rate = getMedianFee();
        int[] feeRateInterval = getFeeRateInterval(rate);
        if(feeRateInterval[1] == Integer.MAX_VALUE) {
            return "VSIZE2000-2200_COLOR";
        }
        int[] nextRateInterval = getFeeRateInterval(rate * 2);
        String from = "VSIZE" + feeRateInterval[0] + "-" + feeRateInterval[1] + "_COLOR";
        String to = "VSIZE" + nextRateInterval[0] + "-" + (nextRateInterval[1] == Integer.MAX_VALUE ? "2200" : nextRateInterval[1]) + "_COLOR";
        return "linear-gradient(from 75% 0% to 100% 0%, " + from + " 0%, " + to + " 100%, " + from +")";
    }

    private int[] getFeeRateInterval(double medianFee) {
        for(int i = 0; i < MEMPOOL_FEE_RATES_INTERVALS.size(); i++) {
            int feeRate = MEMPOOL_FEE_RATES_INTERVALS.get(i);
            int nextFeeRate = (i == MEMPOOL_FEE_RATES_INTERVALS.size() - 1 ? Integer.MAX_VALUE : MEMPOOL_FEE_RATES_INTERVALS.get(i + 1));
            if(feeRate <= medianFee && nextFeeRate > medianFee) {
                return new int[] { feeRate, nextFeeRate };
            }
        }

        return new int[] { 1, 2 };
    }

    public int getWeight() {
        return weightProperty.get();
    }

    public IntegerProperty weightProperty() {
        return weightProperty;
    }

    public void setWeight(int weight) {
        weightProperty.set(weight);
    }

    public double getMedianFee() {
        return medianFeeProperty.get();
    }

    public DoubleProperty medianFee() {
        return medianFeeProperty;
    }

    public void setMedianFee(double medianFee) {
        medianFeeProperty.set(medianFee);
    }

    public int getHeight() {
        return heightProperty.get();
    }

    public IntegerProperty heightProperty() {
        return heightProperty;
    }

    public void setHeight(int height) {
        heightProperty.set(height);
    }

    public int getTxCount() {
        return txCountProperty.get();
    }

    public IntegerProperty txCountProperty() {
        return txCountProperty;
    }

    public void setTxCount(int txCount) {
        txCountProperty.set(txCount);
    }

    public long getTimestamp() {
        return timestampProperty.get();
    }

    public LongProperty timestampProperty() {
        return timestampProperty;
    }

    public void setTimestamp(long timestamp) {
        timestampProperty.set(timestamp);
    }

    public String getElapsed() {
        return elapsedProperty.get();
    }

    public StringProperty elapsedProperty() {
        return elapsedProperty;
    }

    public void setElapsed(String elapsed) {
        elapsedProperty.set(elapsed);
    }

    public boolean isConfirmed() {
        return confirmedProperty.get();
    }

    public BooleanProperty confirmedProperty() {
        return confirmedProperty;
    }

    public void setConfirmed(boolean confirmed) {
        confirmedProperty.set(confirmed);
    }

    public FeeRatesSource getFeeRatesSource() {
        return feeRatesSource.get();
    }

    public ObjectProperty<FeeRatesSource> feeRatesSourceProperty() {
        return feeRatesSource;
    }

    public void setFeeRatesSource(FeeRatesSource feeRatesSource) {
        this.feeRatesSource.set(feeRatesSource);
    }

    public static BlockCube fromBlockSummary(BlockSummary blockSummary) {
        return new BlockCube(blockSummary.getWeight().orElse(0), blockSummary.getMedianFee().orElse(-1.0d), blockSummary.getHeight(),
                blockSummary.getTransactionCount().orElse(0), blockSummary.getTimestamp().getTime(), true);
    }
}