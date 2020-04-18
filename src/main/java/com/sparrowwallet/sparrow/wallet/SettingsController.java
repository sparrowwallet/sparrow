package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.event.WalletChangedEvent;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.RangeSlider;
import org.controlsfx.tools.Borders;
import tornadofx.control.Fieldset;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SettingsController extends WalletFormController implements Initializable {

    @FXML
    private ComboBox<PolicyType> policyType;

    @FXML
    private TextField spendingMiniscript;

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
    private StackPane keystoreTabsPane;

    private TabPane keystoreTabs;

    private final SimpleIntegerProperty totalKeystores = new SimpleIntegerProperty(0);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        Wallet wallet = walletForm.getWallet();

        keystoreTabs = new TabPane();
        keystoreTabsPane.getChildren().add(Borders.wrap(keystoreTabs).etchedBorder().outerPadding(10, 5, 0 ,0).innerPadding(0).raised().buildAll());

        policyType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, policyType) -> {
            wallet.setPolicyType(policyType);
            scriptType.setItems(FXCollections.observableArrayList(ScriptType.getScriptTypesForPolicyType(policyType)));
            scriptType.getSelectionModel().select(policyType.getDefaultScriptType());
            multisigFieldset.setVisible(policyType.equals(PolicyType.MULTI));
            if(policyType.equals(PolicyType.MULTI)) {
                totalKeystores.bind(multisigControl.highValueProperty());
            } else {
                totalKeystores.unbind();
                totalKeystores.set(1);
            }
        });

        scriptType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, scriptType) -> {
            int threshold = wallet.getPolicyType().equals(PolicyType.MULTI) ? (int)multisigControl.lowValueProperty().get() : 1;
            wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), scriptType, wallet.getKeystores(), threshold));
            EventManager.get().post(new WalletChangedEvent(wallet));
        });

        multisigLowLabel.textProperty().bind(multisigControl.lowValueProperty().asString("%.0f") );
        multisigHighLabel.textProperty().bind(multisigControl.highValueProperty().asString("%.0f"));

        multisigControl.lowValueProperty().addListener((observable, oldValue, threshold) -> {
            wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), threshold.intValue()));
            EventManager.get().post(new WalletChangedEvent(wallet));
        });

        multisigFieldset.managedProperty().bind(multisigFieldset.visibleProperty());

        totalKeystores.addListener((observable, oldValue, numCosigners) -> {
            int keystoreCount = wallet.getKeystores().size();
            int keystoreNameCount = keystoreCount;
            while(keystoreCount < numCosigners.intValue()) {
                keystoreCount++;
                String name = "Keystore " + keystoreNameCount;
                while(wallet.getKeystores().stream().map(Keystore::getLabel).collect(Collectors.toList()).contains(name)) {
                    name = "Keystore " + (++keystoreNameCount);
                }
                wallet.getKeystores().add(new Keystore(name));
            }
            wallet.setKeystores(wallet.getKeystores().subList(0, numCosigners.intValue()));

            for(int i = 0; i < wallet.getKeystores().size(); i++) {
                Keystore keystore = wallet.getKeystores().get(i);
                if(keystoreTabs.getTabs().size() == i) {
                    Tab tab = getKeystoreTab(wallet, keystore);
                    keystoreTabs.getTabs().add(tab);
                }
            }
            while(keystoreTabs.getTabs().size() > wallet.getKeystores().size()) {
                keystoreTabs.getTabs().remove(keystoreTabs.getTabs().size() - 1);
            }

            if(wallet.getPolicyType().equals(PolicyType.MULTI)) {
                wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), wallet.getDefaultPolicy().getNumSignaturesRequired()));
                EventManager.get().post(new WalletChangedEvent(wallet));
            }
        });

        if(wallet.getPolicyType() == null) {
            wallet.setPolicyType(PolicyType.SINGLE);
            wallet.setScriptType(ScriptType.P2WPKH);
            wallet.getKeystores().add(new Keystore("Keystore 1"));
            wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), 1));
        }

        if(wallet.getPolicyType().equals(PolicyType.SINGLE)) {
            totalKeystores.setValue(wallet.getKeystores().size());
        } else if(wallet.getPolicyType().equals(PolicyType.MULTI)) {
            multisigControl.highValueProperty().set(wallet.getKeystores().size());
        }

        if(wallet.getPolicyType() != null) {
            policyType.getSelectionModel().select(walletForm.getWallet().getPolicyType());
        } else {
            policyType.getSelectionModel().select(0);
        }

        if(wallet.getScriptType() != null) {
            scriptType.getSelectionModel().select(walletForm.getWallet().getScriptType());
        }
    }

    private Tab getKeystoreTab(Wallet wallet, Keystore keystore) {
        Tab tab = new Tab(keystore.getLabel());
        tab.setClosable(false);

        try {
            FXMLLoader keystoreLoader = new FXMLLoader(AppController.class.getResource("wallet/keystore.fxml"));
            tab.setContent(keystoreLoader.load());
            KeystoreController controller = keystoreLoader.getController();
            controller.setKeystore(getWalletForm(), keystore);
            tab.textProperty().bind(controller.getLabel().textProperty());

            return tab;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Subscribe
    public void updateMiniscript(WalletChangedEvent event) {
        spendingMiniscript.setText(event.getWallet().getDefaultPolicy().getMiniscript().getScript());
    }
}
