package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.UsbDeviceEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.glyphfont.Glyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class DeviceDialog<R> extends Dialog<R> {
    private final List<String> operationFingerprints;
    private final Accordion deviceAccordion;
    private final Button scanButton;
    private final VBox scanBox;
    private final Label scanLabel;

    public DeviceDialog() {
        this(null);
    }

    public DeviceDialog(List<String> operationFingerprints) {
        this.operationFingerprints = operationFingerprints;

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        dialogPane.setContent(stackPane);

        AnchorPane anchorPane = new AnchorPane();
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setPrefHeight(280);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorPane.getChildren().add(scrollPane);
        scrollPane.setFitToWidth(true);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);

        deviceAccordion = new Accordion();
        scrollPane.setContent(deviceAccordion);

        scanBox = new VBox();
        scanBox.setSpacing(30);
        scanBox.setAlignment(Pos.CENTER);
        Glyph usb = new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
        usb.setFontSize(50);
        scanLabel = new Label("Connect Hardware Wallet");
        scanButton = new Button("Scan...");
        scanButton.setPrefSize(120, 60);
        scanButton.setOnAction(event -> {
            scan();
        });
        scanBox.getChildren().addAll(usb, scanLabel, scanButton);
        scanBox.managedProperty().bind(scanBox.visibleProperty());

        stackPane.getChildren().addAll(anchorPane, scanBox);

        List<Device> devices = getDevices();
        if(devices == null || devices.isEmpty()) {
            scanButton.setDefaultButton(true);
            scanBox.setVisible(true);
        } else {
            Platform.runLater(() -> setDevices(devices));
            scanBox.setVisible(false);
        }

        final ButtonType rescanButtonType = new javafx.scene.control.ButtonType("Rescan", ButtonBar.ButtonData.NO);
        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(rescanButtonType, cancelButtonType);

        Button rescanButton = (Button) dialogPane.lookupButton(rescanButtonType);
        rescanButton.managedProperty().bind(rescanButton.visibleProperty());
        rescanButton.visibleProperty().bind(scanBox.visibleProperty().not());
        rescanButton.addEventFilter(ActionEvent.ACTION, event -> {
            scan();
            event.consume();
        });

        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(360);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.moveToActiveWindowScreen(this);

        setResultConverter(dialogButton -> dialogButton == cancelButtonType ? null : getResult());
    }

    protected List<Device> getDevices() {
        return AppServices.getDevices();
    }

    private void scan() {
        Hwi.EnumerateService enumerateService = new Hwi.EnumerateService(null);
        enumerateService.setOnSucceeded(workerStateEvent -> {
            scanButton.setText("Scan...");
            List<Device> devices = enumerateService.getValue();
            setDevices(devices);
            Platform.runLater(() -> EventManager.get().post(new UsbDeviceEvent(devices)));
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            scanButton.setText("Scan...");
            deviceAccordion.getPanes().clear();
            scanButton.setDefaultButton(true);
            scanBox.setVisible(true);
            scanLabel.setText(workerStateEvent.getSource().getException().getMessage());
        });
        enumerateService.setOnRunning(workerStateEvent -> {
            scanButton.setText("Scanning...");
        });
        enumerateService.start();
    }

    protected void setDevices(List<Device> devices) {
        List<Device> dialogDevices = new ArrayList<>(devices);
        dialogDevices.removeIf(Objects::isNull);

        if(operationFingerprints != null) {
            dialogDevices.removeIf(device -> {
                return device.getFingerprint() != null && !operationFingerprints.contains(device.getFingerprint()) && !(device.isNeedsPinSent() || device.isNeedsPassphraseSent());
            });
        }

        deviceAccordion.getPanes().clear();

        if(dialogDevices.isEmpty()) {
            scanButton.setDefaultButton(true);
            scanBox.setVisible(true);
            scanLabel.setText("No matching devices found");
        } else {
            scanBox.setVisible(false);
            for(Device device : dialogDevices) {
                DevicePane devicePane = getDevicePane(device, dialogDevices.size() == 1);
                deviceAccordion.getPanes().add(devicePane);
            }
        }
    }

    protected abstract DevicePane getDevicePane(Device device, boolean defaultDevice);
}
