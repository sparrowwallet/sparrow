package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WebcamMirroredChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
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

    public WebcamView(WebcamService service, boolean mirrored) {
        this.service = service ;
        this.imageView = new ImageView();
        imageView.setPreserveRatio(true);
        if(mirrored) {
            setMirrored(true);
        }

        ContextMenu contextMenu = new ContextMenu();
        CheckMenuItem mirrorItem = new CheckMenuItem("Mirror Camera");
        mirrorItem.setSelected(mirrored);
        mirrorItem.selectedProperty().addListener((observable, oldValue, newValue) -> {
            setMirrored(newValue);
            Config.get().setMirrorCapture(newValue);
            EventManager.get().post(new WebcamMirroredChangedEvent(newValue));
        });
        contextMenu.getItems().add(mirrorItem);
        imageView.setOnContextMenuRequested(event -> {
            contextMenu.show(imageView, event.getScreenX(), event.getScreenY());
        });
        imageView.setOnScroll(scrollEvent -> {
            if(service.isRunning() && scrollEvent.getDeltaY() != 0 && service.getZoomLimits() != null) {
                int currentZoom = service.getZoom();
                if(currentZoom >= 0) {
                    int newZoom = scrollEvent.getDeltaY() > 0 ? Math.round(currentZoom * 1.1f) : Math.round(currentZoom * 0.9f);
                    newZoom = Math.max(newZoom, service.getZoomLimits().getMin());
                    newZoom = Math.min(newZoom, service.getZoomLimits().getMax());
                    if(newZoom != currentZoom) {
                        service.setZoom(newZoom);
                    }
                }
            }
        });

        service.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                imageProperty.set(newValue);
            }
        });

        this.statusPlaceholder = new Label();
        this.view = new Region() {
            {
                service.stateProperty().addListener((obs, oldState, newState) -> {
                    switch(newState) {
                        case READY:
                            if(imageProperty.get() == null) {
                                statusPlaceholder.setText("Initializing");
                                getChildren().setAll(statusPlaceholder);
                            }
                            break;
                        case SCHEDULED:
                            if(imageProperty.get() == null) {
                                statusPlaceholder.setText("Waiting");
                                getChildren().setAll(statusPlaceholder);
                            }
                            break;
                        case RUNNING:
                            if(imageProperty.get() == null) {
                                imageView.imageProperty().unbind();
                                imageView.imageProperty().bind(imageProperty);
                                getChildren().setAll(imageView);
                            }
                            break;
                        case CANCELLED:
                            imageProperty.set(null);
                            imageView.imageProperty().unbind();
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
                });
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                double w = getWidth();
                double h = getHeight();
                if(service.isRunning()) {
                    imageView.setFitWidth(w);
                    imageView.setFitHeight(h);
                    imageView.resizeRelocate(0, 0, w, h);
                } else {
                    double labelHeight = statusPlaceholder.prefHeight(w);
                    double labelWidth = statusPlaceholder.prefWidth(labelHeight);
                    statusPlaceholder.resizeRelocate((w - labelWidth) / 2, (h - labelHeight) / 2, labelWidth, labelHeight);
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

    public void setMirrored(boolean mirrored) {
        imageView.setScaleX(mirrored ? -1 : 1);
    }
}
