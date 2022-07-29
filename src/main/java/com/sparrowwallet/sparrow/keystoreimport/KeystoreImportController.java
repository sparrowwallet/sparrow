package com.sparrowwallet.sparrow.keystoreimport;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Device;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class KeystoreImportController implements Initializable {
    private Wallet wallet;

    @FXML
    private ToggleGroup importMenu;

    @FXML
    private StackPane importPane;

    private KeyDerivation requiredDerivation;
    private WalletModel requiredModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public Wallet getWallet() {
        return wallet;
    }

    public void initializeView(Wallet wallet) {
        this.wallet = wallet;
        importMenu.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
            if(selectedToggle == null) {
                oldValue.setSelected(true);
                return;
            }

            KeystoreSource importType = (KeystoreSource) selectedToggle.getUserData();
            String fxmlName = importType.toString().toLowerCase(Locale.ROOT);
            if(importType == KeystoreSource.SW_SEED || importType == KeystoreSource.SW_WATCH) {
                fxmlName = "sw";
            }
            setImportPane(fxmlName);
        });
    }

    public void selectSource(KeystoreSource keystoreSource, boolean required) {
        for(Toggle toggle : importMenu.getToggles()) {
            if(toggle.getUserData().equals(keystoreSource)) {
                Platform.runLater(() -> importMenu.selectToggle(toggle));
            } else if(required) {
                ((ToggleButton)toggle).setDisable(true);
            }
        }
    }

    void showUsbDevices(List<Device> devices) {
        FXMLLoader loader = setImportPane("hw_usb-devices");
        HwUsbDevicesController controller = loader.getController();
        controller.initializeView(devices);
    }

    void showUsbNone() {
        FXMLLoader loader = setImportPane("hw_usb-none");
        HwUsbScanController controller = loader.getController();
        controller.initializeView("No hardware wallets found");
    }

    void showUsbError(String message) {
        FXMLLoader loader = setImportPane("hw_usb-error");
        HwUsbScanController controller = loader.getController();
        controller.initializeView(message);
    }

    FXMLLoader setImportPane(String fxmlName) {
        importPane.getChildren().removeAll(importPane.getChildren());

        try {
            URL url = AppServices.class.getResource("keystoreimport/" + fxmlName + ".fxml");
            if(url == null) {
                throw new IllegalStateException("Cannot find keystoreimport/" + fxmlName + ".fxml");
            }

            FXMLLoader importLoader = new FXMLLoader(url);
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

    public KeyDerivation getRequiredDerivation() {
        return requiredDerivation;
    }

    public void setRequiredDerivation(KeyDerivation requiredDerivation) {
        this.requiredDerivation = requiredDerivation;
    }

    public WalletModel getRequiredModel() {
        return requiredModel;
    }

    public void setRequiredModel(WalletModel requiredModel) {
        this.requiredModel = requiredModel;
    }
}
