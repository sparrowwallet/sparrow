package com.sparrowwallet.sparrow.control;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

public class AnimationUtil {
    public static Timeline getSlowFadeOut(Node node, Duration duration, double fromValue, int numIncrements) {
        Timeline fadeTimeline = new Timeline();
        Duration incrementDuration = duration.divide(numIncrements);
        for(int i = 0; i < numIncrements; i++) {
            double normalized = ((double)numIncrements - i - 1) / numIncrements;
            double opacity = normalized * fromValue;
            fadeTimeline.getKeyFrames().add(new KeyFrame(incrementDuration.multiply(i+1), event -> node.setOpacity(opacity)));
        }

        return fadeTimeline;
    }
}
