package com.sparrowwallet.sparrow.soroban;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class PayNymController extends SorobanController {
    private static final Logger log = LoggerFactory.getLogger(PayNymController.class);

    private String walletId;
    private PayNym walletPayNym;

    @FXML
    private CopyableTextField payNymName;

    @FXML
    private Button payNymRetrieve;

    @FXML
    private ProgressIndicator retrievePayNymProgress;

    @FXML
    private PaymentCodeTextField paymentCode;

    @FXML
    private CopyableTextField searchPayNyms;

    @FXML
    private ProgressIndicator findPayNym;

    @FXML
    private PayNymAvatar payNymAvatar;

    @FXML
    private ListView<PayNym> followingList;

    @FXML
    private ListView<PayNym> followersList;

    private final ObjectProperty<PayNym> payNymProperty = new SimpleObjectProperty<>(null);

    private final StringProperty findNymProperty = new SimpleStringProperty();

    public void initializeView(String walletId) {
        this.walletId = walletId;

        payNymName.managedProperty().bind(payNymName.visibleProperty());
        payNymRetrieve.managedProperty().bind(payNymRetrieve.visibleProperty());
        payNymRetrieve.visibleProperty().bind(payNymName.visibleProperty().not());

        retrievePayNymProgress.managedProperty().bind(retrievePayNymProgress.visibleProperty());
        retrievePayNymProgress.maxHeightProperty().bind(payNymName.heightProperty());
        retrievePayNymProgress.setVisible(false);

        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getPaymentCode() != null) {
            paymentCode.setPaymentCode(soroban.getPaymentCode());
        }

        findNymProperty.addListener((observable, oldValue, nymIdentifier) -> {
            if(nymIdentifier != null) {
                searchFollowing(nymIdentifier);
            }
        });

        UnaryOperator<TextFormatter.Change> paymentCodeFilter = change -> {
            String input = change.getControlNewText();
            if(input.startsWith("P") && !input.contains("...")) {
                try {
                    PaymentCode paymentCode = new PaymentCode(input);
                    if(paymentCode.isValid()) {
                        findNymProperty.set(input);

                        TextInputControl control = (TextInputControl)change.getControl();
                        change.setText(input.substring(0, 12) + "..." + input.substring(input.length() - 5));
                        change.setRange(0, control.getLength());
                        change.setAnchor(change.getText().length());
                        change.setCaretPosition(change.getText().length());
                    }
                } catch(Exception e) {
                    //ignore
                }
            } else if(PAYNYM_REGEX.matcher(input).matches()) {
                findNymProperty.set(input);
            } else {
                findNymProperty.set(null);
                resetFollowing();
            }

            return change;
        };
        searchPayNyms.setTextFormatter(new TextFormatter<>(paymentCodeFilter));
        findPayNym.managedProperty().bind(findPayNym.visibleProperty());
        findPayNym.maxHeightProperty().bind(searchPayNyms.heightProperty());
        findPayNym.setVisible(false);

        followingList.setCellFactory(param -> {
            return new PayNymCell(this);
        });

        followingList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, payNym) -> {
            payNymProperty.set(payNym);
        });

        followersList.setCellFactory(param -> {
            return new PayNymCell(null);
        });

        followersList.setSelectionModel(new NoSelectionModel<>());
        followersList.setFocusTraversable(false);

        if(Config.get().isUsePayNym() && soroban.getPaymentCode() != null) {
            refresh();
        } else {
            payNymName.setVisible(false);
        }
    }

    private void refresh() {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getPaymentCode() == null) {
            throw new IllegalStateException("Payment code has not been set");
        }
        retrievePayNymProgress.setVisible(true);

        soroban.getPayNym(soroban.getPaymentCode().toString()).subscribe(payNym -> {
            retrievePayNymProgress.setVisible(false);
            walletPayNym = payNym;
            payNymName.setText(payNym.nymName());
            paymentCode.setPaymentCode(payNym.paymentCode());
            payNymAvatar.setPaymentCode(payNym.paymentCode());
            followingList.setUserData(null);
            followingList.setPlaceholder(new Label("No contacts"));
            followingList.setItems(FXCollections.observableList(payNym.following()));
            followersList.setPlaceholder(new Label("No followers"));
            followersList.setItems(FXCollections.observableList(payNym.followers()));
        }, error -> {
            retrievePayNymProgress.setVisible(false);
            if(error.getMessage().endsWith("404")) {
                payNymName.setVisible(false);
            } else {
                log.error("Error retrieving PayNym", error);
                Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not retrieve PayNym. Try again?", ButtonType.CANCEL, ButtonType.OK);
                if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                    refresh();
                } else {
                    payNymName.setVisible(false);
                }
            }
        });
    }

    private void resetFollowing() {
        if(followingList.getUserData() != null) {
            followingList.setUserData(null);
            followingList.setItems(FXCollections.observableList(walletPayNym.following()));
        }
    }

    private void searchFollowing(String nymIdentifier) {
        Optional<PayNym> optExisting = walletPayNym.following().stream().filter(payNym -> payNym.nymName().equals(nymIdentifier) || payNym.paymentCode().toString().equals(nymIdentifier)).findFirst();
        if(optExisting.isPresent()) {
            followingList.setUserData(Boolean.FALSE);
            List<PayNym> existingPayNym = new ArrayList<>();
            existingPayNym.add(optExisting.get());
            followingList.setItems(FXCollections.observableList(existingPayNym));
        } else {
            followingList.setUserData(Boolean.TRUE);
            followingList.setItems(FXCollections.observableList(new ArrayList<>()));
            findPayNym.setVisible(true);

            Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
            soroban.getPayNym(nymIdentifier).subscribe(searchedPayNym -> {
                findPayNym.setVisible(false);
                List<PayNym> searchList = new ArrayList<>();
                searchList.add(searchedPayNym);
                followingList.setUserData(Boolean.TRUE);
                followingList.setItems(FXCollections.observableList(searchList));
            }, error -> {
                findPayNym.setVisible(false);
            });
        }
    }

    public void showQR(ActionEvent event) {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(soroban.getPaymentCode().toString());
        qrDisplayDialog.showAndWait();
    }

    public void scanQR(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        Optional<QRScanDialog.Result> optResult = qrScanDialog.showAndWait();
        if(optResult.isPresent()) {
            QRScanDialog.Result result = optResult.get();
            if(result.payload != null) {
                searchPayNyms.setText(result.payload);
            } else {
                AppServices.showErrorDialog("Invalid QR Code", "Cannot parse QR code into a payment code");
            }
        }
    }

    public void retrievePayNym(ActionEvent event) {
        Config.get().setUsePayNym(true);
        makeAuthenticatedCall(null);
    }

    public void followPayNym(PaymentCode paymentCode) {
        makeAuthenticatedCall(paymentCode);
    }

    private void makeAuthenticatedCall(PaymentCode contact) {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getHdWallet() == null) {
            Wallet wallet = AppServices.get().getWallet(walletId);
            if(wallet.isEncrypted()) {
                Wallet copy = wallet.copy();
                WalletPasswordDialog dlg = new WalletPasswordDialog(copy.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
                Optional<SecureString> password = dlg.showAndWait();
                if(password.isPresent()) {
                    Storage storage = AppServices.get().getOpenWallets().get(wallet);
                    Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(storage, password.get(), true);
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                        ECKey encryptionFullKey = keyDerivationService.getValue();
                        Key key = new Key(encryptionFullKey.getPrivKeyBytes(), storage.getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                        copy.decrypt(key);

                        try {
                            soroban.setHDWallet(copy);
                            makeAuthenticatedCall(soroban, contact);
                        } finally {
                            key.clear();
                            encryptionFullKey.clear();
                            password.get().clear();
                        }
                    });
                    keyDerivationService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                        if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                            Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                                Platform.runLater(() -> makeAuthenticatedCall(contact));
                            }
                        } else {
                            log.error("Error deriving wallet key", keyDerivationService.getException());
                        }
                    });
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                    keyDerivationService.start();
                }
            } else {
                soroban.setHDWallet(wallet);
                makeAuthenticatedCall(soroban, contact);
            }
        } else {
            makeAuthenticatedCall(soroban, contact);
        }
    }

    private void makeAuthenticatedCall(Soroban soroban, PaymentCode contact) {
        if(contact != null) {
            followPayNym(soroban, contact);
        } else {
            retrievePayNym(soroban);
        }
    }

    private void retrievePayNym(Soroban soroban) {
        soroban.createPayNym().subscribe(createMap -> {
            payNymName.setText((String)createMap.get("nymName"));
            payNymAvatar.setPaymentCode(soroban.getPaymentCode());
            payNymName.setVisible(true);

            claimPayNym(soroban, createMap);
            refresh();
        }, error -> {
            log.error("Error retrieving PayNym", error);
            Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not retrieve PayNym. Try again?", ButtonType.CANCEL, ButtonType.OK);
            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                retrievePayNym(soroban);
            }
        });
    }

    private void followPayNym(Soroban soroban, PaymentCode contact) {
        soroban.getAuthToken(new HashMap<>()).subscribe(authToken -> {
            String signature = soroban.getSignature(authToken);
            soroban.followPaymentCode(contact, authToken, signature).subscribe(followMap -> {
                refresh();
            }, error -> {
                log.error("Could not follow payment code", error);
                Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not follow payment code. Try again?", ButtonType.CANCEL, ButtonType.OK);
                if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                    followPayNym(soroban, contact);
                } else {
                    followingList.refresh();
                }
            });
        }, error -> {
            log.error("Could not follow payment code", error);
            Optional<ButtonType> optResponse = showErrorDialog("Error retrieving PayNym", "Could not follow payment code. Try again?", ButtonType.CANCEL, ButtonType.OK);
            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                followPayNym(soroban, contact);
            } else {
                followingList.refresh();
            }
        });
    }

    public PayNym getPayNym() {
        return payNymProperty.get();
    }

    public ObjectProperty<PayNym> payNymProperty() {
        return payNymProperty;
    }

    public static class NoSelectionModel<T> extends MultipleSelectionModel<T> {

        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return FXCollections.emptyObservableList();
        }

        @Override
        public ObservableList<T> getSelectedItems() {
            return FXCollections.emptyObservableList();
        }

        @Override
        public void selectIndices(int index, int... indices) {
        }

        @Override
        public void selectAll() {
        }

        @Override
        public void selectFirst() {
        }

        @Override
        public void selectLast() {
        }

        @Override
        public void clearAndSelect(int index) {
        }

        @Override
        public void select(int index) {
        }

        @Override
        public void select(T obj) {
        }

        @Override
        public void clearSelection(int index) {
        }

        @Override
        public void clearSelection() {
        }

        @Override
        public boolean isSelected(int index) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void selectPrevious() {
        }

        @Override
        public void selectNext() {
        }
    }
}
