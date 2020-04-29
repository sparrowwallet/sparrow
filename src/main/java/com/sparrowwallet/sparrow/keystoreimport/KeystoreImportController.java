package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.external.Device;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class KeystoreImportController implements Initializable {
    private Wallet wallet;

    @FXML
    private ToggleGroup importMenu;

    @FXML
    private StackPane importPane;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public Wallet getWallet() {
        return wallet;
    }

    public void initializeView(Wallet wallet) {
        this.wallet = wallet;
        importMenu.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
            KeystoreSource importType = (KeystoreSource) selectedToggle.getUserData();
            System.out.println(importType);
            setImportPane(importType.toString().toLowerCase());
        });
    }

    void showUsbDevices(List<Device> devices) {
        FXMLLoader loader = setImportPane("hw_usb-devices");
        UsbDevicesController controller = loader.getController();
        controller.initializeView(devices);
    }

    void showUsbError(String message) {
        FXMLLoader loader = setImportPane("hw_usb-error");
        UsbScanController controller = loader.getController();
        controller.initializeView(message);
    }

    FXMLLoader setImportPane(String fxmlName) {
        importPane.getChildren().removeAll(importPane.getChildren());

        try {
            FXMLLoader importLoader = new FXMLLoader(AppController.class.getResource("keystoreimport/" + fxmlName + ".fxml"));
            Node importTypeNode = importLoader.load();
            KeystoreImportDetailController controller = importLoader.getController();
            controller.setMasterController(this);
            controller.initializeView();
            importPane.getChildren().add(importTypeNode);

            return importLoader;
        } catch (IOException e) {
            throw new IllegalStateException("Can't find pane", e);
        }
    }
}
