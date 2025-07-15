package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.net.FeeRatesSource;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.util.StringConverter;

import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.*;

public class FeeRangeSlider extends Slider {
    private static final double FEE_RATE_SCROLL_INCREMENT = 0.01;

    public FeeRangeSlider() {
        super(0, FEE_RATES_RANGE.size() - 1, 0);
        setMajorTickUnit(1);
        setMinorTickCount(0);
        setSnapToTicks(false);
        setShowTickLabels(true);
        setShowTickMarks(true);
        setBlockIncrement(Math.log(1.02) / Math.log(2));

        setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double object) {
                Double feeRate = DOUBLE_FEE_RATES_RANGE.get(object.intValue());
                if(isDoubleFeeRange() && feeRate >= 1000) {
                    return feeRate / 1000 + "k";
                }
                return feeRate < 1 ? Double.toString(feeRate) : String.format("%.0f", feeRate);
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });

        updateTrackHighlight();

        valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                updateMaxFeeRange(newValue.doubleValue());
            }
        });

        setOnScroll(event -> {
            if(event.getDeltaY() != 0) {
                double newFeeRate = getFeeRate() + (event.getDeltaY() > 0 ? FEE_RATE_SCROLL_INCREMENT : -FEE_RATE_SCROLL_INCREMENT);
                if(newFeeRate < DOUBLE_FEE_RATES_RANGE.get(0)) {
                    newFeeRate = DOUBLE_FEE_RATES_RANGE.get(0);
                } else if(newFeeRate > DOUBLE_FEE_RATES_RANGE.get(DOUBLE_FEE_RATES_RANGE.size() - 1)) {
                    newFeeRate = DOUBLE_FEE_RATES_RANGE.get(DOUBLE_FEE_RATES_RANGE.size() - 1);
                }
                setFeeRate(newFeeRate);
            }
        });
    }

    public double getFeeRate() {
        double value = getValue();
        // First range: 0.01, 0.02, 0.04, 0.08 and smooth values in between
        if(value < 3) return 0.01 * Math.pow(2, value);
        // Transition from 0.08 to 0.1 (smoothly, using factor 1.25)
        if(value < 4) return 0.08 * Math.pow(1.25, value - 3);
        // Second binary range: 0.1, 0.2, 0.4, 0.8 and smooth values in between
        if(value < 7) return 0.1 * Math.pow(2, value - 4);
        // Transition from 0.8 to 1.0 (smoothly, using factor 1.25)
        if(value < 8) return 0.8 * Math.pow(1.25, value - 7);
        // Third binary range: 1, 2, 4, 8, ... and smooth values in between
        return Math.pow(2, value - 8);
    }

    public void setFeeRate(double feeRate) {
        double value = Math.log(feeRate) / Math.log(2);
        updateMaxFeeRange(value);
        setValue(value);
    }

    private void updateMaxFeeRange(double value) {
        if(value >= getMax() && !isDoubleFeeRange()) {
            setMin(FEE_RATES_RANGE.size() - 2);
            setMax(DOUBLE_FEE_RATES_RANGE.size() - 1);
            updateTrackHighlight();
        } else if(value == getMin() && isDoubleFeeRange()) {
            setMin(0);
            setMax(FEE_RATES_RANGE.size() - 1);
            updateTrackHighlight();
        }
    }

    private boolean isDoubleFeeRange() {
        return getMax() > FEE_RATES_RANGE.size() - 1;
    }

    public void updateTrackHighlight() {
        addFeeRangeTrackHighlight(0);
    }

    private void addFeeRangeTrackHighlight(int count) {
        Platform.runLater(() -> {
            Node track = lookup(".track");
            if(track != null) {
                Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
                String highlight = "";
                if(targetBlocksFeeRates.get(Integer.MAX_VALUE) != null) {
                    highlight += "#a0a1a766 " + getPercentageOfFeeRange(targetBlocksFeeRates.get(Integer.MAX_VALUE)) + "%, ";
                }
                highlight += "#41a9c966 " + getPercentageOfFeeRange(targetBlocksFeeRates, FeeRatesSource.BLOCKS_IN_TWO_HOURS - 1) + "%, ";
                highlight += "#fba71b66 " + getPercentageOfFeeRange(targetBlocksFeeRates, FeeRatesSource.BLOCKS_IN_HOUR - 1) + "%, ";
                highlight += "#c8416466 " + getPercentageOfFeeRange(targetBlocksFeeRates, FeeRatesSource.BLOCKS_IN_HALF_HOUR - 1) + "%";

                track.setStyle("-fx-background-color: " +
                        "-fx-shadow-highlight-color, " +
                        "linear-gradient(to bottom, derive(-fx-text-box-border, -10%), -fx-text-box-border), " +
                        "linear-gradient(to bottom, derive(-fx-control-inner-background, -9%), derive(-fx-control-inner-background, 0%), derive(-fx-control-inner-background, -5%), derive(-fx-control-inner-background, -12%)), " +
                        "linear-gradient(to right, " + highlight + ")");
            } else if(count < 20) {
                addFeeRangeTrackHighlight(count+1);
            }
        });
    }

    private Map<Integer, Double> getTargetBlocksFeeRates() {
        Map<Integer, Double> retrievedFeeRates = AppServices.getTargetBlockFeeRates();
        if(retrievedFeeRates == null) {
            retrievedFeeRates = TARGET_BLOCKS_RANGE.stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> getFallbackFeeRate(),
                    (u, v) -> { throw new IllegalStateException("Duplicate target blocks"); },
                    LinkedHashMap::new));
        }

        return retrievedFeeRates;
    }

    private int getPercentageOfFeeRange(Map<Integer, Double> targetBlocksFeeRates, Integer minTargetBlocks) {
        List<Integer> rates = new ArrayList<>(targetBlocksFeeRates.keySet());
        Collections.reverse(rates);
        for(Integer targetBlocks : rates) {
            if(targetBlocks < minTargetBlocks) {
                return getPercentageOfFeeRange(targetBlocksFeeRates.get(targetBlocks));
            }
        }

        return 100;
    }

    private int getPercentageOfFeeRange(Double feeRate) {
        double index = Math.log(feeRate) / Math.log(2);
        if(isDoubleFeeRange()) {
            index *= ((double)FEE_RATES_RANGE.size() / (DOUBLE_FEE_RATES_RANGE.size())) * 0.99;
        }
        return (int)Math.round(index * 10.0);
    }
}
