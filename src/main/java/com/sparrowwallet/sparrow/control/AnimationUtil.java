package com.sparrowwallet.sparrow.control;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

public class AnimationUtil {
    public static Timeline getSlowFadeOut(Node node, Duration duration, double fromValue, int numIncrements) {
        Timeline fadeTimeline = new Timeline();
        Duration incrementDuration = duration.divide(numIncrements);
        for(int i = 0; i < numIncrements; i++) {
            double percent = ((double)numIncrements - i - 1) / numIncrements;
            double opacity = percent * fromValue;
            fadeTimeline.getKeyFrames().add(new KeyFrame(incrementDuration.multiply(i+1), event -> node.setOpacity(opacity)));
        }

        return fadeTimeline;
    }

    public static Timeline getPulse(Node node, Duration duration, double fromValue, double toValue, int numIncrements) {
        Timeline pulseTimeline = getFade(node, duration, fromValue, toValue, numIncrements);

        pulseTimeline.setCycleCount(Animation.INDEFINITE);
        pulseTimeline.setAutoReverse(true);

        return pulseTimeline;
    }

    public static Timeline getFade(Node node, Duration duration, double fromValue, double toValue, int numIncrements) {
        Timeline fadeTimeline = new Timeline();
        Duration incrementDuration = duration.divide(numIncrements);
        for(int i = 0; i < numIncrements; i++) {
            double percent = ((double) numIncrements - i - 1) / numIncrements; //From 99% to 0%
            double opacity = (percent * (fromValue - toValue)) + toValue;
            fadeTimeline.getKeyFrames().add(new KeyFrame(incrementDuration.multiply(i+1), event -> node.setOpacity(opacity)));
        }

        return fadeTimeline;
    }

    public record AnimatedNode (Node node, Timeline timeline) {}
}
