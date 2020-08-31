package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.protocol.TransactionSignature;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.KeystoreSignedEvent;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.controlsfx.control.SegmentedBar;

import java.util.ArrayList;
import java.util.List;

public class SignaturesProgressBar extends SegmentedBar<SignaturesProgressBar.SignatureProgressSegment> {
    public SignaturesProgressBar() {
        setOrientation(Orientation.HORIZONTAL);
        setSegmentViewFactory(SignatureProgressSegmentView::new);
        setInfoNodeFactory(segment -> segment.getKeystore() == null ? null : new SignatureProgressSegmentLabel(segment.getKeystore().getLabel()));
    }

    public void initialize(ObservableMap<TransactionSignature, Keystore> signatureKeystoreMap, int threshold) {
        getStyleClass().add("signatures-progress-bar");
        getSegments().clear();

        List<Keystore> signedKeystores = new ArrayList<>(signatureKeystoreMap.values());
        int numSegments = Math.max(threshold, signedKeystores.size());
        double segmentSize = 100d / numSegments;
        for(int i = 0; i < numSegments; i++) {
            if(i < signedKeystores.size()) {
                getSegments().add(new SignatureProgressSegment(segmentSize, i, signedKeystores.get(i)));
            } else {
                getSegments().add(new SignatureProgressSegment(segmentSize, i, null));
            }
        }

        signatureKeystoreMap.addListener((MapChangeListener<TransactionSignature, Keystore>) c -> {
            List<Keystore> newSignedKeystores = new ArrayList<>(c.getMap().values());
            int newNumSegments = Math.max(threshold, newSignedKeystores.size());
            double newSegmentSize = 100d / newNumSegments;

            for(int i = 0; i < newNumSegments; i++) {
                SignatureProgressSegment segment = null;
                if(i < getSegments().size()) {
                    segment = getSegments().get(i);
                }

                Keystore signedKeystore = null;
                if(i < newSignedKeystores.size()) {
                    signedKeystore = newSignedKeystores.get(i);
                }

                if(segment != null) {
                    //Animate new signature if changed
                    segment.setKeystore(signedKeystore);
                } else {
                    //Add extra (unnecessary) signature
                    for(SignaturesProgressBar.SignatureProgressSegment existingSegment : getSegments()) {
                        existingSegment.setValue(newSegmentSize);
                    }

                    SignaturesProgressBar.SignatureProgressSegment newSegment = new SignatureProgressSegment(newSegmentSize, i, null);
                    getSegments().add(newSegment);
                    newSegment.setKeystore(signedKeystore);
                }
            }
        });
    }

    public static class SignatureProgressSegment extends SegmentedBar.Segment {
        private final SimpleObjectProperty<Keystore> keystoreProperty;
        private final int index;

        public SignatureProgressSegment(double value, int index, Keystore keystore) {
            super(value);
            this.index = index;

            this.keystoreProperty = new SimpleObjectProperty<>(this, "keystore", null);
            keystoreProperty.addListener((observable, oldValue, newValue) -> {
                setText(newValue == null ? "No keystore" : newValue.getLabel());
            });

            setKeystore(keystore);
        }

        public int getIndex() {
            return index;
        }

        public Keystore getKeystore() {
            return keystoreProperty.get();
        }

        public SimpleObjectProperty<Keystore> keystoreProperty() {
            return keystoreProperty;
        }

        public void setKeystore(Keystore keystore) {
            keystoreProperty.set(keystore);
        }

        public void signatureCompleted() {
            EventManager.get().post(new KeystoreSignedEvent(getKeystore()));
        }
    }

    public static class SignatureProgressSegmentView extends StackPane {
        private final ProgressBar progressBar;
        private final Label label;

        public SignatureProgressSegmentView(SignatureProgressSegment segment) {
            getStyleClass().add("signature-progress-segment");
            getStyleClass().add("segment" + segment.getIndex());

            label = new Label();
            label.textProperty().bind(segment.textProperty());
            label.getStyleClass().add("signature-progress-segment-label");
            StackPane.setAlignment(label, Pos.CENTER);

            progressBar = new ProgressBar(segment.getKeystore() == null ? 0.0 : 1.0);
            progressBar.setPrefWidth(Double.MAX_VALUE);
            progressBar.setPrefHeight(30);

            setPrefHeight(50);
            getChildren().addAll(progressBar, label);

            segment.keystoreProperty().addListener((observable, oldValue, newValue) -> {
                if(oldValue == null && newValue != null) {
                    Timeline timeline = new Timeline(
                            new KeyFrame(Duration.ZERO, new KeyValue(progressBar.progressProperty(), 0)),
                            new KeyFrame(Duration.millis(800), e -> {
                                segment.signatureCompleted();
                            }, new KeyValue(progressBar.progressProperty(), 1))
                    );
                    timeline.setCycleCount(1);
                    timeline.play();
                }
            });
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            label.setVisible(label.prefWidth(-1) < getWidth() - getPadding().getLeft() - getPadding().getRight() - 8);
        }
    }

    public static class SignatureProgressSegmentLabel extends Label {
        public SignatureProgressSegmentLabel(String text) {
            super(text);
            setPadding(new Insets(10));
        }
    }
}
