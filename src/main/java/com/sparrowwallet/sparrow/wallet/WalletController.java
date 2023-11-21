package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.ViewPasswordField;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.textfield.CustomPasswordField;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class WalletController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(WalletController.class);

    @FXML
    private StackPane walletPane;

    @FXML
    private VBox walletMenuBox;

    @FXML
    private ToggleGroup walletMenu;

    private BorderPane lockPane;

    private CustomPasswordField passwordField;

    private final BooleanProperty walletEncryptedProperty = new SimpleBooleanProperty(false);

    private final ChangeListener<Boolean> lockFocusListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            if(newValue && getWalletForm().isLocked() && passwordField != null && passwordField.isVisible()) {
                passwordField.requestFocus();
            }
        }
    };

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
                } else if(function != Function.LOCK) {
                    walletFunction.setViewOrder(1);
                }
            }

            try {
                if(!existing) {
                    URL url = AppServices.class.getResource("wallet/" + function.toString().toLowerCase(Locale.ROOT) + ".fxml");
                    if(url == null) {
                        throw new IllegalStateException("Cannot find wallet/" + function.toString().toLowerCase(Locale.ROOT) + ".fxml");
                    }

                    FXMLLoader functionLoader = new FXMLLoader(url);
                    Node walletFunction = functionLoader.load();
                    walletFunction.setUserData(function);
                    WalletFormController controller = functionLoader.getController();

                    WalletForm walletForm = getWalletForm();
                    if(function.equals(Function.SETTINGS)) {
                        walletForm = new SettingsWalletForm(getWalletForm().getStorage(), getWalletForm().getWallet(), getWalletForm());
                        getWalletForm().setSettingsWalletForm(walletForm);
                    }

                    controller.setWalletForm(walletForm);
                    walletFunction.setViewOrder(1);
                    walletPane.getChildren().add(walletFunction);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Can't find pane", e);
            }
        });

        for(Toggle toggle : walletMenu.getToggles()) {
            ToggleButton toggleButton = (ToggleButton) toggle;
            toggleButton.managedProperty().bind(toggleButton.visibleProperty());
        }

        walletMenuBox.managedProperty().bind(walletMenuBox.visibleProperty());
        walletMenuBox.visibleProperty().bind(getWalletForm().lockedProperty().not());

        configure(walletForm.getWallet());
    }

    public void configure(Wallet wallet) {
        boolean validWallet = wallet.isValid();
        boolean whirlpoolChildWallet = wallet.isWhirlpoolChildWallet();

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
                ((ToggleButton)toggle).setVisible(!(whirlpoolChildWallet && toggle.getUserData().equals(Function.RECEIVE)));
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

    private void initializeLockScreen() {
        lockPane = new BorderPane();
        lockPane.setUserData(Function.LOCK);
        lockPane.getStyleClass().add("wallet-pane");
        VBox vBox = new VBox(20);
        vBox.setAlignment(Pos.CENTER);
        Glyph lock = new Glyph("FontAwesome", FontAwesome.Glyph.LOCK);
        lock.setFontSize(80);
        vBox.getChildren().add(lock);
        Label label = new Label("Enter password to unlock:");
        label.managedProperty().bind(label.visibleProperty());
        label.visibleProperty().bind(walletEncryptedProperty);
        passwordField = new ViewPasswordField();
        passwordField.setMaxWidth(300);
        passwordField.managedProperty().bind(passwordField.visibleProperty());
        passwordField.visibleProperty().bind(walletEncryptedProperty);
        passwordField.setOnAction(event -> {
            unlockWallet(passwordField);
        });
        Button unlockButton = new Button("Unlock");
        unlockButton.setPrefWidth(300);
        unlockButton.setOnAction(event -> {
            unlockWallet(passwordField);
        });
        vBox.getChildren().addAll(label, passwordField, unlockButton);
        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(vBox);
        lockPane.setCenter(stackPane);
        walletPane.getChildren().add(lockPane);

        walletPane.getScene().getWindow().focusedProperty().addListener(new WeakChangeListener<>(lockFocusListener));
    }

    private void unlockWallet(CustomPasswordField passwordField) {
        if(walletEncryptedProperty.get()) {
            String walletId = walletForm.getWalletId();
            SecureString password = new SecureString(passwordField.getText());
            Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(walletForm.getStorage(), password, true);
            keyDerivationService.setOnSucceeded(workerStateEvent -> {
                passwordField.clear();
                password.clear();
                EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                unlockWallet();
            });
            keyDerivationService.setOnFailed(workerStateEvent -> {
                EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                    showErrorDialog("Invalid Password", "The wallet password was invalid.");
                } else {
                    log.error("Error deriving wallet key", keyDerivationService.getException());
                }
            });
            EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
            keyDerivationService.start();
        } else {
            unlockWallet();
        }
    }

    private void unlockWallet() {
        Wallet masterWallet = getWalletForm().getWallet().isMasterWallet() ? getWalletForm().getWallet() : getWalletForm().getWallet().getMasterWallet();
        EventManager.get().post(new WalletUnlockEvent(masterWallet));
    }

    private void updateWalletEncryptedStatus() {
        try {
            walletEncryptedProperty.set(getWalletForm().getStorage().isEncrypted());
        } catch(IOException e) {
            log.warn("Error determining if wallet is locked", e);
        }
    }

    @Subscribe
    public void walletAddressesChanged(WalletAddressesChangedEvent event) {
        if(event.getWalletId().equals(walletForm.getWalletId())) {
            configure(event.getWallet());
        }
    }

    @Subscribe
    public void walletSettingsChanged(WalletSettingsChangedEvent event) {
        if(event.getWalletId().equals(walletForm.getWalletId())) {
            Platform.runLater(this::updateWalletEncryptedStatus);
        }
    }

    @Subscribe
    public void functionAction(FunctionActionEvent event) {
        if(event.selectFunction() && event.getWallet().equals(walletForm.getWallet())) {
            selectFunction(event.getFunction());
        }
    }

    @Subscribe
    public void walletLock(WalletLockEvent event) {
        if(event.getWallet().equals(walletForm.getMasterWallet())) {
            if(lockPane == null) {
                updateWalletEncryptedStatus();
                initializeLockScreen();
            }

            getWalletForm().setLocked(true);
            lockPane.setViewOrder(-1);
        }
    }

    @Subscribe
    public void walletUnlock(WalletUnlockEvent event) {
        if(event.getWallet().equals(walletForm.getMasterWallet())) {
            getWalletForm().setLocked(false);
            if(lockPane != null) {
                lockPane.setViewOrder(2);
            }
        }
    }
}
