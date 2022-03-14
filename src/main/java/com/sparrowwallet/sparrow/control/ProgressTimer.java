package com.sparrowwallet.sparrow.control;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ProgressIndicator;
import javafx.util.Duration;

public class ProgressTimer extends ProgressIndicator {
    private final IntegerProperty secondsProperty = new SimpleIntegerProperty(60);

    private Timeline timeline;

    public ProgressTimer() {
        super(0);
        getStyleClass().add("progress-timer");
    }

    public void start() {
        start(e -> {});
    }

    public void start(EventHandler<ActionEvent> onFinished) {
        getStyleClass().remove("warn");
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progressProperty(), 0)),
                new KeyFrame(Duration.seconds(getSeconds() * 0.8), e -> getStyleClass().add("warn")),
                new KeyFrame(Duration.seconds(getSeconds()), onFinished, new KeyValue(progressProperty(), 1)));
        timeline.setCycleCount(1);
        timeline.play();
    }

    public void stop() {
        if(timeline != null) {
            timeline.stop();
        }
    }

    public int getSeconds() {
        return secondsProperty.get();
    }

    public IntegerProperty secondsProperty() {
        return secondsProperty;
    }

    public void setSeconds(int secondsProperty) {
        this.secondsProperty.set(secondsProperty);
    }
}
