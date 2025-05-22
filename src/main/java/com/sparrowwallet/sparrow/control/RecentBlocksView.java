package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.BlockSummary;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.FeeRatesSource;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import javafx.animation.TranslateTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.sparrowwallet.sparrow.AppServices.TARGET_BLOCKS_RANGE;
import static com.sparrowwallet.sparrow.control.BlockCube.CUBE_SIZE;

public class RecentBlocksView extends Pane {
    private static final double CUBE_SPACING = 100;
    private static final double ANIMATION_DURATION_MILLIS = 1000;
    private static final double SEPARATOR_X = 74;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private final ObjectProperty<List<BlockCube>> cubesProperty = new SimpleObjectProperty<>(new ArrayList<>());
    private final Tooltip tooltip = new Tooltip();

    public RecentBlocksView() {
        cubesProperty.addListener((_, _, newValue) -> {
            if(newValue != null && newValue.size() == 3) {
                drawView();
            }
        });

        Rectangle clip = new Rectangle(-20, -40, CUBE_SPACING * 3 - 20, 100);
        setClip(clip);

        Observable<Long> intervalObservable = Observable.interval(1, TimeUnit.MINUTES);
        disposables.add(intervalObservable.observeOn(JavaFxScheduler.platform()).subscribe(_ -> {
            for(BlockCube cube : getCubes()) {
                cube.setElapsed(BlockCube.getElapsed(cube.getTimestamp()));
            }
        }));

        FeeRatesSource feeRatesSource = Config.get().getFeeRatesSource();
        feeRatesSource = (feeRatesSource == null ? FeeRatesSource.MEMPOOL_SPACE : feeRatesSource);
        updateFeeRatesSource(feeRatesSource);
        Tooltip.install(this, tooltip);
    }

    public void updateFeeRatesSource(FeeRatesSource feeRatesSource) {
        tooltip.setText("Fee rates from " + feeRatesSource.getDescription());
        if(getCubes() != null && !getCubes().isEmpty()) {
            getCubes().getFirst().setFeeRatesSource(feeRatesSource);
        }
    }

    public void drawView() {
        createSeparator();

        for(int i = 0; i < 3; i++) {
            BlockCube cube = getCubes().get(i);
            cube.setTranslateX(i * CUBE_SPACING);
            getChildren().add(cube);
        }
    }

    private void createSeparator() {
        Line separator = new Line(SEPARATOR_X, -9, SEPARATOR_X, CUBE_SIZE);
        separator.getStyleClass().add("blocks-separator");
        separator.getStrokeDashArray().addAll(5.0, 5.0); // Create dotted line pattern
        separator.setStrokeWidth(1.0);
        getChildren().add(separator);
    }

    public void update(List<BlockSummary> latestBlocks, Double currentFeeRate) {
        if(getCubes().isEmpty()) {
            List<BlockCube> cubes = new ArrayList<>();
            cubes.add(new BlockCube(null, currentFeeRate, null, null, 0L, false));
            cubes.addAll(latestBlocks.stream().map(BlockCube::fromBlockSummary).limit(2).toList());
            setCubes(cubes);
        } else {
            int knownTip = getCubes().stream().mapToInt(BlockCube::getHeight).max().orElse(0);
            int latestTip = latestBlocks.stream().mapToInt(BlockSummary::getHeight).max().orElse(0);
            if(latestTip > knownTip) {
                addNewBlock(latestBlocks, currentFeeRate);
            } else {
                for(int i = 1; i < getCubes().size() && i <= latestBlocks.size(); i++) {
                    BlockCube blockCube = getCubes().get(i);
                    BlockSummary latestBlock = latestBlocks.get(i - 1);
                    blockCube.setConfirmed(true);
                    blockCube.setHeight(latestBlock.getHeight());
                    blockCube.setTimestamp(latestBlock.getTimestamp().getTime());
                    blockCube.setWeight(latestBlock.getWeight().orElse(0));
                    blockCube.setMedianFee(latestBlock.getMedianFee().orElse(-1.0d));
                    blockCube.setTxCount(latestBlock.getTransactionCount().orElse(0));
                }
                updateFeeRate(currentFeeRate);
            }
        }
    }

    private void addNewBlock(List<BlockSummary> latestBlocks, Double currentFeeRate) {
        if(getCubes().isEmpty()) {
            return;
        }

        for(int i = 0; i < getCubes().size() && i < latestBlocks.size(); i++) {
            BlockCube blockCube = getCubes().get(i);
            BlockSummary latestBlock = latestBlocks.get(i);
            blockCube.setConfirmed(true);
            blockCube.setHeight(latestBlock.getHeight());
            blockCube.setTimestamp(latestBlock.getTimestamp().getTime());
            blockCube.setWeight(latestBlock.getWeight().orElse(0));
            blockCube.setMedianFee(latestBlock.getMedianFee().orElse(-1.0d));
            blockCube.setTxCount(latestBlock.getTransactionCount().orElse(0));
        }

        add(new BlockCube(null, currentFeeRate, null, null, 0L, false));
    }

    public void add(BlockCube newCube) {
        newCube.setTranslateX(-CUBE_SPACING);
        getChildren().add(newCube);
        getCubes().getFirst().setConfirmed(true);
        getCubes().addFirst(newCube);
        animateCubes();
        if(getCubes().size() > 4) {
            BlockCube lastCube = getCubes().getLast();
            getChildren().remove(lastCube);
            getCubes().remove(lastCube);
        }
    }

    public void updateFeeRate(Map<Integer, Double> targetBlockFeeRates) {
        int defaultTarget = TARGET_BLOCKS_RANGE.get((TARGET_BLOCKS_RANGE.size() / 2) - 1);
        if(targetBlockFeeRates.get(defaultTarget) != null) {
            Double defaultRate = targetBlockFeeRates.get(defaultTarget);
            updateFeeRate(defaultRate);
        }
    }

    public void updateFeeRate(Double currentFeeRate) {
        if(!getCubes().isEmpty()) {
            BlockCube firstCube = getCubes().getFirst();
            firstCube.setMedianFee(currentFeeRate);
        }
    }

    private void animateCubes() {
        for(int i = 0; i < getCubes().size(); i++) {
            BlockCube cube = getCubes().get(i);
            TranslateTransition transition = new TranslateTransition(Duration.millis(ANIMATION_DURATION_MILLIS), cube);
            transition.setToX(i * CUBE_SPACING);
            transition.play();
        }
    }

    public List<BlockCube> getCubes() {
        return cubesProperty.get();
    }

    public ObjectProperty<List<BlockCube>> cubesProperty() {
        return cubesProperty;
    }

    public void setCubes(List<BlockCube> cubes) {
        this.cubesProperty.set(cubes);
    }
}
