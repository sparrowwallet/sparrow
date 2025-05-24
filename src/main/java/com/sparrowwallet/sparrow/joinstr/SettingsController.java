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
    TextField nostrRelayTextField;

    @Override
    public void initializeView() {

        try {

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

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
