package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.controlsfx.control.RangeSlider;
import tornadofx.control.Fieldset;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController extends WalletFormController implements Initializable {

    @FXML
    private ComboBox<PolicyType> policyType;

    @FXML
    private TextField policy;

    @FXML
    private ComboBox<ScriptType> scriptType;

    @FXML
    private Fieldset multisigFieldset;

    @FXML
    private RangeSlider multisigControl;

    @FXML
    private CopyableLabel multisigLowLabel;

    @FXML
    private CopyableLabel multisigHighLabel;

    @FXML
    private TabPane keystoreTabs;

    @FXML ComboBox testType;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        policyType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, policyType) -> {
            scriptType.getSelectionModel().select(policyType.getDefaultScriptType());
            multisigFieldset.setVisible(policyType.equals(PolicyType.MULTI));
        });

        multisigLowLabel.textProperty().bind(multisigControl.lowValueProperty().asString("%.0f") );
        multisigHighLabel.textProperty().bind(multisigControl.highValueProperty().asString("%.0f"));

        multisigFieldset.managedProperty().bind(multisigFieldset.visibleProperty());

        if(walletForm.getWallet().getPolicyType() != null) {
            policyType.getSelectionModel().select(walletForm.getWallet().getPolicyType());
        } else {
            policyType.getSelectionModel().select(0);
        }


    }
}
