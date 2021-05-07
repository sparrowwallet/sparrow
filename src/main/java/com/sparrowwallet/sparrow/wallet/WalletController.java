package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ReceiveActionEvent;
import com.sparrowwallet.sparrow.event.SendActionEvent;
import com.sparrowwallet.sparrow.event.WalletAddressesChangedEvent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class WalletController extends WalletFormController implements Initializable {
    @FXML
    private BorderPane tabContent;

    @FXML
    private StackPane walletPane;

    @FXML
    private ToggleGroup walletMenu;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void initializeView() {
        walletMenu.selectedToggleProperty().addListener((observable, oldValue, selectedToggle) -> {
            if(selectedToggle == null) {
                oldValue.setSelected(true);
                return;
            }

            Function function = (Function)selectedToggle.getUserData();

            boolean existing = false;
            for(Node walletFunction : walletPane.getChildren()) {
                if(walletFunction.getUserData().equals(function)) {
                    existing = true;
                    walletFunction.setViewOrder(0);
                } else {
                    walletFunction.setViewOrder(1);
                }
            }

            try {
                if(!existing) {
                    FXMLLoader functionLoader = new FXMLLoader(AppServices.class.getResource("wallet/" + function.toString().toLowerCase() + ".fxml"));
                    Node walletFunction = functionLoader.load();
                    walletFunction.setUserData(function);
                    WalletFormController controller = functionLoader.getController();

                    WalletForm walletForm = getWalletForm();
                    if(function.equals(Function.SETTINGS)) {
                        walletForm = new SettingsWalletForm(getWalletForm().getStorage(), getWalletForm().getWallet());
                    }

                    controller.setWalletForm(walletForm);
                    walletFunction.setViewOrder(1);
                    walletPane.getChildren().add(walletFunction);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Can't find pane", e);
            }
        });

        configure(walletForm.getWallet().isValid());
    }

    public void configure(boolean validWallet) {
        for(Toggle toggle : walletMenu.getToggles()) {
            if(toggle.getUserData().equals(Function.SETTINGS)) {
                if(!validWallet) {
                    toggle.setSelected(true);
                }
            } else {
                if(toggle.getUserData().equals(Function.TRANSACTIONS) && validWallet && walletMenu.getSelectedToggle() == null) {
                    toggle.setSelected(true);
                }

                ((ToggleButton)toggle).setDisable(!validWallet);
            }
        }
    }

    public void selectFunction(Function function) {
        Platform.runLater(() -> {
            for(Toggle toggle : walletMenu.getToggles()) {
                if(toggle.getUserData().equals(function)) {
                    toggle.setSelected(true);
                }
            }
        });
    }

    @Subscribe
    public void walletAddressesChanged(WalletAddressesChangedEvent event) {
        if(event.getWalletFile().equals(walletForm.getWalletFile())) {
            configure(event.getWallet().isValid());
        }
    }

    @Subscribe
    public void receiveAction(ReceiveActionEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            selectFunction(Function.RECEIVE);
        }
    }

    @Subscribe
    public void sendAction(SendActionEvent event) {
        if(!event.getUtxos().isEmpty() && event.getWallet().equals(walletForm.getWallet())) {
            selectFunction(Function.SEND);
        }
    }
}
