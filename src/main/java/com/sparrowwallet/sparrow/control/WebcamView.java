package com.sparrowwallet.sparrow.control;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebcamView {
    private static final Logger log = LoggerFactory.getLogger(WebcamView.class);

    private final ImageView imageView;
    private final WebcamService service;
    private final Region view;

    private final Label statusPlaceholder;

    private final ObjectProperty<Image> imageProperty = new SimpleObjectProperty<>(null);

    public WebcamView(WebcamService service) {
        this.service = service ;
        this.imageView = new ImageView();
        imageView.setPreserveRatio(true);
        // make the cam behave like a mirror:
        imageView.setScaleX(-1);

        service.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                imageProperty.set(newValue);
            }
        });

        this.statusPlaceholder = new Label();
        this.view = new Region() {
            {
                service.stateProperty().addListener((obs, oldState, newState) -> {
                    switch (newState) {
                        case READY:
                            if(imageProperty.get() == null) {
                                statusPlaceholder.setText("Initializing");
                                getChildren().setAll(statusPlaceholder);
                            }
                            break ;
                        case SCHEDULED:
                            if(imageProperty.get() == null) {
                                statusPlaceholder.setText("Waiting");
                                getChildren().setAll(statusPlaceholder);
                            }
                            break ;
                        case RUNNING:
                            imageView.imageProperty().unbind();
                            imageView.imageProperty().bind(imageProperty);
                            getChildren().setAll(imageView);
                            break ;
                        case CANCELLED:
                            imageView.imageProperty().unbind();
                            imageView.setImage(null);
                            statusPlaceholder.setText("Stopped");
                            getChildren().setAll(statusPlaceholder);
                            break;
                        case FAILED:
                            imageView.imageProperty().unbind();
                            statusPlaceholder.setText("Error");
                            getChildren().setAll(statusPlaceholder);
                            log.error("Failed to start web cam", service.getException());
                            break;
                        case SUCCEEDED:
                            // unreachable...
                            imageView.imageProperty().unbind();
                            statusPlaceholder.setText("");
                            getChildren().clear();
                    }
                    requestLayout();
                });
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                double w = getWidth();
                double h = getHeight();
                if (service.isRunning()) {
                    imageView.setFitWidth(w);
                    imageView.setFitHeight(h);
                    imageView.resizeRelocate(0, 0, w, h);
                } else {
                    double labelHeight = statusPlaceholder.prefHeight(w);
                    double labelWidth = statusPlaceholder.prefWidth(labelHeight);
                    statusPlaceholder.resizeRelocate((w - labelWidth)/2, (h-labelHeight)/2, labelWidth, labelHeight);
                }
            }

            @Override
            protected double computePrefWidth(double height) {
                return service.getCamWidth();
            }
            @Override
            protected double computePrefHeight(double width) {
                return service.getCamHeight();
            }
        };
    }

    public WebcamService getService() {
        return service;
    }

    public Node getView() {
        return view;
    }
}
