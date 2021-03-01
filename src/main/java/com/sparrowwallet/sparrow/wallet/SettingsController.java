package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import org.controlsfx.control.RangeSlider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Fieldset;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class SettingsController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    @FXML
    private ComboBox<PolicyType> policyType;

    @FXML
    private DescriptorArea descriptor;

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

    @FXML
    private Button apply;

    @FXML
    private Button revert;

    private final SimpleIntegerProperty totalKeystores = new SimpleIntegerProperty(0);

    private boolean initialising = true;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        keystoreTabs = new TabPane();
        keystoreTabsPane.getChildren().add(keystoreTabs);

        policyType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, policyType) -> {
            walletForm.getWallet().setPolicyType(policyType);

            scriptType.setItems(FXCollections.observableArrayList(ScriptType.getAddressableScriptTypes(policyType)));
            if(!ScriptType.getAddressableScriptTypes(policyType).contains(walletForm.getWallet().getScriptType())) {
                scriptType.getSelectionModel().select(policyType.getDefaultScriptType());
            }

            if(!initialising) {
                clearKeystoreTabs();
            }
            initialising = false;

            multisigFieldset.setVisible(policyType.equals(PolicyType.MULTI));
            if(policyType.equals(PolicyType.MULTI)) {
                totalKeystores.bind(multisigControl.highValueProperty());
            } else {
                totalKeystores.set(1);
            }
        });

        scriptType.setConverter(new StringConverter<>() {
            @Override
            public String toString(ScriptType scriptType) {
                return scriptType == null ? "" : scriptType.getDescription();
            }

            @Override
            public ScriptType fromString(String string) {
                return Arrays.stream(ScriptType.values()).filter(type -> type.getDescription().equals(string)).findFirst().orElse(null);
            }
        });

        scriptType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, scriptType) -> {
            if(scriptType != null) {
                walletForm.getWallet().setScriptType(scriptType);
            }

            EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.SCRIPT_TYPE));
        });

        multisigLowLabel.textProperty().bind(multisigControl.lowValueProperty().asString("%.0f") );
        multisigHighLabel.textProperty().bind(multisigControl.highValueProperty().asString("%.0f"));

        multisigControl.lowValueProperty().addListener((observable, oldValue, threshold) -> {
            EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.MUTLISIG_THRESHOLD));
        });

        multisigFieldset.managedProperty().bind(multisigFieldset.visibleProperty());

        totalKeystores.addListener((observable, oldValue, numCosigners) -> {
            int keystoreCount = walletForm.getWallet().getKeystores().size();
            int keystoreNameCount = keystoreCount + 1;
            while(keystoreCount < numCosigners.intValue()) {
                keystoreCount++;
                String name = "Keystore " + keystoreNameCount;
                while(walletForm.getWallet().getKeystores().stream().map(Keystore::getLabel).collect(Collectors.toList()).contains(name)) {
                    name = "Keystore " + (++keystoreNameCount);
                }
                Keystore keystore = new Keystore(name);
                keystore.setSource(KeystoreSource.SW_WATCH);
                keystore.setWalletModel(WalletModel.SPARROW);
                walletForm.getWallet().getKeystores().add(keystore);
            }
            List<Keystore> newKeystoreList = new ArrayList<>(walletForm.getWallet().getKeystores().subList(0, numCosigners.intValue()));
            walletForm.getWallet().getKeystores().clear();
            walletForm.getWallet().getKeystores().addAll(newKeystoreList);

            for(int i = 0; i < walletForm.getWallet().getKeystores().size(); i++) {
                Keystore keystore = walletForm.getWallet().getKeystores().get(i);
                if(keystoreTabs.getTabs().size() == i) {
                    Tab tab = getKeystoreTab(walletForm.getWallet(), keystore);
                    keystoreTabs.getTabs().add(tab);
                }
            }
            while(keystoreTabs.getTabs().size() > walletForm.getWallet().getKeystores().size()) {
                keystoreTabs.getTabs().remove(keystoreTabs.getTabs().size() - 1);
            }

            if(walletForm.getWallet().getPolicyType().equals(PolicyType.MULTI)) {
                EventManager.get().post(new SettingsChangedEvent(walletForm.getWallet(), SettingsChangedEvent.Type.MULTISIG_TOTAL));
            }
        });

        initializeDescriptorField(descriptor);

        revert.setOnAction(event -> {
            keystoreTabs.getTabs().removeAll(keystoreTabs.getTabs());
            totalKeystores.unbind();
            totalKeystores.setValue(0);
            walletForm.revert();
            initialising = true;
            setFieldsFromWallet(walletForm.getWallet());
        });

        apply.setOnAction(event -> {
            revert.setDisable(true);
            apply.setDisable(true);
            saveWallet(false, false);
        });

        setFieldsFromWallet(walletForm.getWallet());
    }

    private void clearKeystoreTabs() {
        totalKeystores.unbind();
        totalKeystores.set(0);
    }

    private void setFieldsFromWallet(Wallet wallet) {
        if(wallet.getPolicyType() == null) {
            wallet.setPolicyType(PolicyType.SINGLE);
            wallet.setScriptType(ScriptType.P2WPKH);
            Keystore keystore = new Keystore("Keystore 1");
            keystore.setSource(KeystoreSource.SW_WATCH);
            keystore.setWalletModel(WalletModel.SPARROW);
            wallet.getKeystores().add(keystore);
            wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), 1));
        }

        if(wallet.getPolicyType().equals(PolicyType.SINGLE)) {
            totalKeystores.setValue(1);
        } else if(wallet.getPolicyType().equals(PolicyType.MULTI)) {
            multisigControl.lowValueProperty().set(wallet.getDefaultPolicy().getNumSignaturesRequired());
            multisigControl.highValueProperty().set(wallet.getKeystores().size());
            totalKeystores.bind(multisigControl.highValueProperty());
        }

        if(wallet.getPolicyType() != null) {
            policyType.getSelectionModel().select(walletForm.getWallet().getPolicyType());
        }

        if(wallet.getScriptType() != null) {
            scriptType.getSelectionModel().select(walletForm.getWallet().getScriptType());
        }

        revert.setDisable(true);
        apply.setDisable(true);
    }

    private Tab getKeystoreTab(Wallet wallet, Keystore keystore) {
        Tab tab = new Tab(keystore.getLabel());
        tab.setClosable(false);

        try {
            FXMLLoader keystoreLoader = new FXMLLoader(AppServices.class.getResource("wallet/keystore.fxml"));
            tab.setContent(keystoreLoader.load());
            KeystoreController controller = keystoreLoader.getController();
            controller.setKeystore(getWalletForm(), keystore);
            tab.textProperty().bind(controller.getLabel().textProperty());
            tab.setUserData(keystore);

            controller.getValidationSupport().validationResultProperty().addListener((o, oldValue, result) -> {
                if(result.getErrors().isEmpty()) {
                    tab.getStyleClass().remove("tab-error");
                    tab.setTooltip(null);
                } else {
                    if(!tab.getStyleClass().contains("tab-error")) {
                        tab.getStyleClass().add("tab-error");
                    }
                    tab.setTooltip(new Tooltip(result.getErrors().iterator().next().getText()));
                }
            });

            return tab;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void scanDescriptorQR(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.outputDescriptor != null) {
                setDescriptorText(result.outputDescriptor.toString());
            } else if(result.payload != null && !result.payload.isEmpty()) {
                setDescriptorText(result.payload);
            } else if(result.exception != null) {
                AppServices.showErrorDialog("Error scanning QR", result.exception.getMessage());
            }
        }
    }

    public void editDescriptor(ActionEvent event) {
        OutputDescriptor outputDescriptor = OutputDescriptor.getOutputDescriptor(walletForm.getWallet());
        String outputDescriptorString = outputDescriptor.toString(walletForm.getWallet().isValid());

        TextAreaDialog dialog = new TextAreaDialog(outputDescriptorString);
        dialog.setTitle("Edit wallet output descriptor");
        dialog.getDialogPane().setHeaderText("Wallet output descriptor:");
        Optional<String> text = dialog.showAndWait();
        if(text.isPresent() && !text.get().isEmpty() && !text.get().equals(outputDescriptorString)) {
            setDescriptorText(text.get());
        }
    }

    private void setDescriptorText(String text) {
        try {
            OutputDescriptor editedOutputDescriptor = OutputDescriptor.getOutputDescriptor(text.trim().replace("\\", ""));
            Wallet editedWallet = editedOutputDescriptor.toWallet();

            editedWallet.setName(getWalletForm().getWallet().getName());
            keystoreTabs.getTabs().removeAll(keystoreTabs.getTabs());
            totalKeystores.unbind();
            totalKeystores.setValue(0);
            walletForm.setWallet(editedWallet);
            initialising = true;
            setFieldsFromWallet(editedWallet);

            EventManager.get().post(new SettingsChangedEvent(editedWallet, SettingsChangedEvent.Type.POLICY));
        } catch(Exception e) {
            AppServices.showErrorDialog("Invalid output descriptor", e.getMessage());
        }
    }

    public void showAdvanced(ActionEvent event) {
        AdvancedDialog advancedDialog = new AdvancedDialog(walletForm);
        Optional<Boolean> optApply = advancedDialog.showAndWait();
        if(optApply.isPresent() && optApply.get() && walletForm.getWallet().isValid()) {
            revert.setDisable(true);
            apply.setDisable(true);
            saveWallet(false, true);
        }
    }

    @Override
    protected String describeKeystore(Keystore keystore) {
        if(!keystore.isValid()) {
            for(Tab tab : keystoreTabs.getTabs()) {
                if(tab.getUserData() == keystore && tab.getTooltip() != null) {
                    return tab.getTooltip().getText();
                }
            }
        }

        return super.describeKeystore(keystore);
    }

    @Subscribe
    public void update(SettingsChangedEvent event) {
        Wallet wallet = event.getWallet();
        if(walletForm.getWallet().equals(wallet)) {
            if(wallet.getPolicyType() == PolicyType.SINGLE) {
                wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), 1));
            } else if(wallet.getPolicyType() == PolicyType.MULTI) {
                wallet.setDefaultPolicy(Policy.getPolicy(wallet.getPolicyType(), wallet.getScriptType(), wallet.getKeystores(), (int)multisigControl.getLowValue()));
            }

            if(ScriptType.getAddressableScriptTypes(wallet.getPolicyType()).contains(wallet.getScriptType())) {
                descriptor.setWallet(wallet);
            }

            revert.setDisable(false);
            apply.setDisable(!wallet.isValid());
        }
    }

    @Subscribe
    public void walletSettingsChanged(WalletSettingsChangedEvent event) {
        updateBirthDate(event.getWalletFile(), event.getWallet());
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        updateBirthDate(event.getWalletFile(), event.getWallet());
    }

    private void updateBirthDate(File walletFile, Wallet wallet) {
        if(walletFile.equals(walletForm.getWalletFile()) && !Objects.equals(wallet.getBirthDate(), walletForm.getWallet().getBirthDate())) {
            walletForm.getWallet().setBirthDate(wallet.getBirthDate());
        }
    }

    private void saveWallet(boolean changePassword, boolean suggestChangePassword) {
        ECKey existingPubKey = walletForm.getStorage().getEncryptionPubKey();

        WalletPasswordDialog.PasswordRequirement requirement;
        if(existingPubKey == null) {
            if(changePassword) {
                requirement = WalletPasswordDialog.PasswordRequirement.UPDATE_CHANGE;
            } else {
                requirement = WalletPasswordDialog.PasswordRequirement.UPDATE_NEW;
            }
        } else if(Storage.NO_PASSWORD_KEY.equals(existingPubKey)) {
            requirement = WalletPasswordDialog.PasswordRequirement.UPDATE_EMPTY;
        } else {
            requirement = WalletPasswordDialog.PasswordRequirement.UPDATE_SET;
        }

        if(!changePassword && ((SettingsWalletForm)walletForm).isAddressChange() && !walletForm.getWallet().getTransactions().isEmpty()) {
            Optional<ButtonType> optResponse = AppServices.showWarningDialog("Change Wallet Addresses?", "This wallet has existing transactions which will be replaced as the wallet addresses will change. Ok to proceed?", ButtonType.CANCEL, ButtonType.OK);
            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.CANCEL)) {
                revert.setDisable(false);
                apply.setDisable(false);
                return;
            }
        }

        WalletPasswordDialog dlg = new WalletPasswordDialog(null, requirement, suggestChangePassword);
        Optional<SecureString> password = dlg.showAndWait();
        if(password.isPresent()) {
            if(dlg.isBackupExisting()) {
                try {
                    walletForm.saveBackup();
                } catch(IOException e) {
                    log.error("Error saving wallet backup", e);
                    AppServices.showErrorDialog("Error saving wallet backup", e.getMessage());
                    revert.setDisable(false);
                    apply.setDisable(false);
                    return;
                }
            }

            if(password.get().length() == 0 && requirement != WalletPasswordDialog.PasswordRequirement.UPDATE_SET) {
                try {
                    walletForm.getStorage().setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
                    walletForm.saveAndRefresh();
                    if(requirement == WalletPasswordDialog.PasswordRequirement.UPDATE_NEW || requirement == WalletPasswordDialog.PasswordRequirement.UPDATE_CHANGE) {
                        EventManager.get().post(new RequestOpenWalletsEvent());
                    }
                } catch (IOException e) {
                    log.error("Error saving wallet", e);
                    AppServices.showErrorDialog("Error saving wallet", e.getMessage());
                    revert.setDisable(false);
                    apply.setDisable(false);
                }
            } else {
                Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(walletForm.getStorage(), password.get());
                keyDerivationService.setOnSucceeded(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletForm.getWalletFile(), TimedEvent.Action.END, "Done"));
                    ECKey encryptionFullKey = keyDerivationService.getValue();
                    Key key = null;

                    try {
                        ECKey encryptionPubKey = ECKey.fromPublicOnly(encryptionFullKey);

                        if(existingPubKey != null && !Storage.NO_PASSWORD_KEY.equals(existingPubKey) && !existingPubKey.equals(encryptionPubKey)) {
                            AppServices.showErrorDialog("Incorrect Password", "The password was incorrect.");
                            revert.setDisable(false);
                            apply.setDisable(false);
                            return;
                        }

                        key = new Key(encryptionFullKey.getPrivKeyBytes(), walletForm.getStorage().getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);

                        if(dlg.isChangePassword()) {
                            if(dlg.isDeleteBackups()) {
                                walletForm.deleteBackups();
                            }

                            walletForm.getStorage().setEncryptionPubKey(null);
                            walletForm.getWallet().decrypt(key);
                            saveWallet(true, false);
                            return;
                        }

                        if(dlg.isDeleteBackups()) {
                            walletForm.deleteBackups();
                        }

                        walletForm.getWallet().encrypt(key);
                        walletForm.getStorage().setEncryptionPubKey(encryptionPubKey);
                        walletForm.saveAndRefresh();

                        if(requirement == WalletPasswordDialog.PasswordRequirement.UPDATE_NEW || requirement == WalletPasswordDialog.PasswordRequirement.UPDATE_EMPTY) {
                            EventManager.get().post(new RequestOpenWalletsEvent());
                        }
                    } catch (Exception e) {
                        log.error("Error saving wallet", e);
                        AppServices.showErrorDialog("Error saving wallet", e.getMessage());
                        revert.setDisable(false);
                        apply.setDisable(false);
                    } finally {
                        encryptionFullKey.clear();
                        if(key != null) {
                            key.clear();
                        }
                    }
                });
                keyDerivationService.setOnFailed(workerStateEvent -> {
                    EventManager.get().post(new StorageEvent(walletForm.getWalletFile(), TimedEvent.Action.END, "Failed"));
                    AppServices.showErrorDialog("Error saving wallet", keyDerivationService.getException().getMessage());
                    revert.setDisable(false);
                    apply.setDisable(false);
                });
                EventManager.get().post(new StorageEvent(walletForm.getWalletFile(), TimedEvent.Action.START, "Encrypting wallet..."));
                keyDerivationService.start();
            }
        } else {
            revert.setDisable(false);
            apply.setDisable(false);
        }
    }
}
