package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

public class ConfirmationProgressIndicator extends StackPane {
    private final Group confirmationGroup;
    private final Arc arc;
    private final Line downTickLine;
    private final Line upTickLine;

    public ConfirmationProgressIndicator(int confirmations) {
        Circle circle = new Circle(7, 7, 7);
        circle.setFill(Color.TRANSPARENT);
        circle.getStyleClass().add("confirmation-progress-circle");

        arc = new Arc(7, 7, 7, 7, 90, getDegrees(confirmations));
        arc.setType(ArcType.ROUND);
        arc.getStyleClass().add("confirmation-progress-arc");

        downTickLine = new Line(4, 8, 4, 8);
        downTickLine.setStrokeWidth(1.0);
        downTickLine.setOpacity(0);
        downTickLine.getStyleClass().add("confirmation-progress-tick");
        upTickLine = new Line(6, 10, 6, 10);
        upTickLine.setStrokeWidth(1.0);
        upTickLine.setOpacity(0);
        upTickLine.getStyleClass().add("confirmation-progress-tick");

        confirmationGroup = new Group(circle, arc, downTickLine, upTickLine);
        getStyleClass().add("confirmation-progress");

        setAlignment(Pos.CENTER);
        getChildren().addAll(confirmationGroup);

        confirmationsProperty().set(confirmations);
        confirmationsProperty().addListener((observable, oldValue, newValue) -> {
            if(!oldValue.equals(newValue)) {
                SequentialTransition sequence = new SequentialTransition();

                Timeline arcLengthTimeline = new Timeline();
                KeyValue arcLengthValue = new KeyValue(arc.lengthProperty(), getDegrees(newValue.intValue()));
                KeyFrame arcLengthFrame = new KeyFrame(Duration.millis(3000), arcLengthValue);
                arcLengthTimeline.getKeyFrames().add(arcLengthFrame);
                sequence.getChildren().add(arcLengthTimeline);

                if(newValue.intValue() >= BlockTransactionHash.BLOCKS_TO_CONFIRM) {
                    Timeline arcRadiusTimeline = new Timeline();
                    KeyValue arcRadiusXValue = new KeyValue(arc.radiusXProperty(), 0.0);
                    KeyValue arcRadiusYValue = new KeyValue(arc.radiusYProperty(), 0.0);
                    KeyFrame arcRadiusFrame = new KeyFrame(Duration.millis(500), arcRadiusXValue, arcRadiusYValue);
                    arcRadiusTimeline.getKeyFrames().add(arcRadiusFrame);
                    sequence.getChildren().add(arcRadiusTimeline);

                    FadeTransition downTickFadeIn = new FadeTransition(Duration.millis(50), downTickLine);
                    downTickFadeIn.setFromValue(0);
                    downTickFadeIn.setToValue(1);
                    sequence.getChildren().add(downTickFadeIn);

                    Timeline downTickLineTimeline = new Timeline();
                    KeyValue downTickLineX = new KeyValue(downTickLine.endXProperty(), 6);
                    KeyValue downTickLineY = new KeyValue(downTickLine.endYProperty(), 10);
                    KeyFrame downTickLineFrame = new KeyFrame(Duration.millis(125), downTickLineX, downTickLineY);
                    downTickLineTimeline.getKeyFrames().add(downTickLineFrame);
                    sequence.getChildren().add(downTickLineTimeline);

                    FadeTransition upTickFadeIn = new FadeTransition(Duration.millis(50), upTickLine);
                    upTickFadeIn.setFromValue(0);
                    upTickFadeIn.setToValue(1);
                    sequence.getChildren().add(upTickFadeIn);

                    Timeline upTickLineTimeline = new Timeline();
                    KeyValue upTickLineX = new KeyValue(upTickLine.endXProperty(), 10);
                    KeyValue upTickLineY = new KeyValue(upTickLine.endYProperty(), 4);
                    KeyFrame upTickLineFrame = new KeyFrame(Duration.millis(250), upTickLineX, upTickLineY);
                    upTickLineTimeline.getKeyFrames().add(upTickLineFrame);
                    sequence.getChildren().add(upTickLineTimeline);

                    Timeline groupFadeOut = AnimationUtil.getSlowFadeOut(confirmationGroup, Duration.minutes(10), 1.0, 10);
                    sequence.getChildren().add(groupFadeOut);

                    confirmationsProperty().unbind();
                }

                sequence.play();
            }
        });
    }

    private static double getDegrees(int confirmations) {
        int requiredConfirmations = BlockTransactionHash.BLOCKS_TO_CONFIRM;
        return ((double)Math.min(confirmations, requiredConfirmations)/ requiredConfirmations) * -360d;
    }

    /**
     * Defines the number of confirmations
     */
    private IntegerProperty confirmations;

    public final void setConfirmations(int value) {
        if(confirmations != null || value != 0) {
            confirmationsProperty().set(value);
        }
    }

    public final int getConfirmations() {
        return confirmations == null ? 0 : confirmations.get();
    }

    public final IntegerProperty confirmationsProperty() {
        if(confirmations == null) {
            confirmations = new IntegerPropertyBase(0) {

                @Override
                public Object getBean() {
                    return ConfirmationProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "confirmations";
                }
            };
        }
        return confirmations;
    }
}
