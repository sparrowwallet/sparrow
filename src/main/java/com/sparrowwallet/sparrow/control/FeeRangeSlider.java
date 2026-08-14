package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.net.FeeRatesSource;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.util.StringConverter;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.*;

public class FeeRangeSlider extends Slider {
    private static final double FEE_RATE_SCROLL_INCREMENT = 0.01;
    private static final DecimalFormat INTEGER_FEE_RATE_FORMAT = new DecimalFormat("0");
    private static final DecimalFormat FRACTIONAL_FEE_RATE_FORMAT = new DecimalFormat("0.###");

    public FeeRangeSlider() {
        super(0, AppServices.getFeeRatesRange().size() - 1, 0);
        setMajorTickUnit(1);
        setMinorTickCount(0);
        setSnapToTicks(false);
        setShowTickLabels(true);
        setShowTickMarks(true);
        setBlockIncrement(Math.log(1.02) / Math.log(2));

        setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double object) {
                Double feeRate = AppServices.getLongFeeRatesRange().get(object.intValue());
                if(isLongFeeRange() && feeRate >= 1000) {
                    return INTEGER_FEE_RATE_FORMAT.format(feeRate / 1000) + "k";
                }
                return feeRate > 0d && feeRate < Transaction.DEFAULT_MIN_RELAY_FEE ? FRACTIONAL_FEE_RATE_FORMAT.format(feeRate) : INTEGER_FEE_RATE_FORMAT.format(feeRate);
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
                if(newFeeRate < AppServices.getLongFeeRatesRange().getFirst()) {
                    newFeeRate = AppServices.getLongFeeRatesRange().getFirst();
                } else if(newFeeRate > AppServices.getLongFeeRatesRange().getLast()) {
                    newFeeRate = AppServices.getLongFeeRatesRange().getLast();
                }
                setFeeRate(newFeeRate);
            }
        });
    }

    public double getFeeRate() {
        return getFeeRate(AppServices.getMinimumRelayFeeRate());
    }

    public double getFeeRate(Double minRelayFeeRate) {
        if(minRelayFeeRate >= Transaction.DEFAULT_MIN_RELAY_FEE) {
            return Math.pow(2.0, getValue());
        }

        if(getValue() < 1.0d) {
            if(minRelayFeeRate == 0.0d) {
                return getValue();
            }
            return Math.pow(minRelayFeeRate, 1.0d - getValue());
        }

        return Math.pow(2.0, getValue() - 1.0d);
    }

    public void setFeeRate(double feeRate) {
        setFeeRate(feeRate, AppServices.getMinimumRelayFeeRate());
    }

    public void setFeeRate(double feeRate, Double minRelayFeeRate) {
        double value = getValue(feeRate, minRelayFeeRate);
        updateMaxFeeRange(value);
        setValue(value);
    }

    private double getValue(double feeRate, Double minRelayFeeRate) {
        double value;

        if(minRelayFeeRate >= Transaction.DEFAULT_MIN_RELAY_FEE) {
            value = Math.log(feeRate) / Math.log(2);
        } else {
            if(feeRate < Transaction.DEFAULT_MIN_RELAY_FEE) {
                if(minRelayFeeRate == 0.0d) {
                    return feeRate;
                }
                value = 1.0d - (Math.log(feeRate) / Math.log(minRelayFeeRate));
            } else {
                value = (Math.log(feeRate) / Math.log(2.0)) + 1.0d;
            }
        }

        return value;
    }

    public void updateFeeRange(Double minRelayFeeRate, Double previousMinRelayFeeRate) {
        if(minRelayFeeRate != null && previousMinRelayFeeRate != null) {
            setFeeRate(getFeeRate(previousMinRelayFeeRate), minRelayFeeRate);
        }
        setMinorTickCount(1);
        setMinorTickCount(0);
    }

    private void updateMaxFeeRange(double value) {
        if(value >= getMax() && !isLongFeeRange()) {
            if(AppServices.getMinimumRelayFeeRate() < Transaction.DEFAULT_MIN_RELAY_FEE) {
                setMin(1.0d);
            }
            setMax(AppServices.getLongFeeRatesRange().size() - 1);
            updateTrackHighlight();
        } else if(value == getMin() && isLongFeeRange()) {
            if(AppServices.getMinimumRelayFeeRate() < Transaction.DEFAULT_MIN_RELAY_FEE) {
                setMin(0.0d);
            }
            setMax(AppServices.getFeeRatesRange().size() - 1);
            updateTrackHighlight();
        }
    }

    public boolean isLongFeeRange() {
        return getMax() > AppServices.getFeeRatesRange().size() - 1;
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
        double index = getValue(feeRate, AppServices.getMinimumRelayFeeRate());
        if(isLongFeeRange()) {
            index *= ((double)AppServices.getFeeRatesRange().size() / (AppServices.getLongFeeRatesRange().size())) * 0.99;
        }
        return (int)Math.round(index * 10.0);
    }
}
