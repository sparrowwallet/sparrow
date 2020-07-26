package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.glyphfont.Glyph;

import java.util.List;

public class DeviceSignDialog extends Dialog<PSBT> {
    private final PSBT psbt;
    private final Accordion deviceAccordion;
    private final VBox scanBox;
    private final Label scanLabel;

    public DeviceSignDialog(PSBT psbt) {
        this.psbt = psbt;

        EventManager.get().register(this);

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppController.class.getResource("general.css").toExternalForm());

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
        Button button = new Button("Scan...");
        button.setPrefSize(120, 60);
        button.setOnAction(event -> {
            scan();
        });
        scanBox.getChildren().addAll(usb, scanLabel, button);
        scanBox.managedProperty().bind(scanBox.visibleProperty());

        stackPane.getChildren().addAll(anchorPane, scanBox);

        List<Device> devices = AppController.getDevices();
        if(devices == null || devices.isEmpty()) {
            scanBox.setVisible(true);
        } else {
            setDevices(devices);
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

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? psbt : null);
    }

    private void scan() {
        Hwi.EnumerateService enumerateService = new Hwi.EnumerateService(null);
        enumerateService.setOnSucceeded(workerStateEvent -> {
            List<Device> devices = enumerateService.getValue();
            setDevices(devices);
        });
        enumerateService.setOnFailed(workerStateEvent -> {
            scanBox.setVisible(true);
            scanLabel.setText(workerStateEvent.getSource().getException().getMessage());
        });
        enumerateService.start();
    }

    private void setDevices(List<Device> devices) {
        deviceAccordion.getPanes().clear();

        if(devices.isEmpty()) {
            scanBox.setVisible(true);
            scanLabel.setText("No devices found");
        } else {
            scanBox.setVisible(false);
            for(Device device : devices) {
                DevicePane devicePane = new DevicePane(DevicePane.DeviceOperation.SIGN, psbt, device);
                deviceAccordion.getPanes().add(devicePane);
            }
        }
    }

    @Subscribe
    public void psbtSigned(PSBTSignedEvent event) {
        if(psbt == event.getPsbt()) {
            setResult(event.getSignedPsbt());
            this.close();
        }
    }
}
