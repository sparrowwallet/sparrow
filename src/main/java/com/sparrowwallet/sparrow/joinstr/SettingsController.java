package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.sparrow.io.Config;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;

public class SettingsController extends JoinstrFormController {

    @FXML
    Label urlLabel;

    @FXML
    Label selectedWalletLabel;

    @FXML
    TextField usernameTextField;

    @FXML
    PasswordField passwordTextField;

    @FXML
    TextField nostrRelayTextField;

    @FXML
    TextField hostTextField;

    @FXML
    TextField locationTextField;

    @FXML
    TextField ipAddressTextField;

    @FXML
    TextField portTextField;

    @FXML
    TextField protocolTextField;

    private VpnGateway vpnGateway;

    @Override
    public void initializeView() {

        try {

            // Node settings
            urlLabel.setText(Config.get().getServer().getUrl());
            selectedWalletLabel.setText(this.getJoinstrForm().getWallet().getName());

            usernameTextField.setText(Config.get().getNodeUsername());
            usernameTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    Config.get().setNodeUsername(usernameTextField.getText());
                }
            });

            passwordTextField.setText(Config.get().getNodePassword());
            passwordTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    Config.get().setNodePassword(passwordTextField.getText());
                }
            });

            nostrRelayTextField.setText(Config.get().getNostrRelay());
            nostrRelayTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    if(nostrRelayTextField.getText().isEmpty()) {
                        nostrRelayTextField.setText("wss://nostr.fmt.wiz.biz");
                    }
                    Config.get().setNostrRelay(nostrRelayTextField.getText());
                }
            });

            // VPN Gateway settings
            vpnGateway = Config.get().getVpnGateway();
            if(vpnGateway == null)
                vpnGateway = new VpnGateway();

            hostTextField.setText(vpnGateway.getHost());
            hostTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    vpnGateway.setHost(hostTextField.getText());
                    Config.get().setVpnGateway(vpnGateway);
                }
            });

            locationTextField.setText(vpnGateway.getLocation());
            locationTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    vpnGateway.setLocation(locationTextField.getText());
                    Config.get().setVpnGateway(vpnGateway);
                }
            });

            ipAddressTextField.setText(vpnGateway.getIpAddress());
            ipAddressTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    vpnGateway.setIpAddress(ipAddressTextField.getText());
                    Config.get().setVpnGateway(vpnGateway);
                }
            });

            portTextField.setText(String.valueOf(vpnGateway.getPort()));
            portTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    if (!newValue.matches("\\d{0,4}")) {
                        portTextField.setText(newValue.replaceAll("[^\\d]", ""));
                    }
                    if (newValue.length() > 4) {
                        portTextField.setText(newValue.substring(0, 4));
                    }
                    vpnGateway.setPort(Integer.getInteger(portTextField.getText()));
                    Config.get().setVpnGateway(vpnGateway);
                }
            });

            protocolTextField.setText(vpnGateway.getProtocol());
            protocolTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable,
                                    String oldValue, String newValue) {
                    vpnGateway.setProtocol(protocolTextField.getText());
                    Config.get().setVpnGateway(vpnGateway);
                }
            });

        } catch(Exception e) {
            if(e != null) {}
        }
    }

}
