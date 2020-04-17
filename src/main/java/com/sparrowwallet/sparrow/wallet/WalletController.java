package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
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
            Function function = (Function)selectedToggle.getUserData();

            boolean existing = false;
            for(Node walletFunction : walletPane.getChildren()) {
                if(walletFunction.getUserData().equals(function)) {
                    existing = true;
                    walletFunction.setViewOrder(1);
                } else {
                    walletFunction.setViewOrder(0);
                }
            }

            try {
                if(!existing) {
                    FXMLLoader functionLoader = new FXMLLoader(AppController.class.getResource("wallet/" + function.toString().toLowerCase() + ".fxml"));
                    Node walletFunction = functionLoader.load();
                    WalletFormController controller = functionLoader.getController();
                    controller.setWalletForm(getWalletForm());
                    walletFunction.setViewOrder(1);
                    walletPane.getChildren().add(walletFunction);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Can't find pane", e);
            }
        });
    }
}
