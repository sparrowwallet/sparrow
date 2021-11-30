package com.sparrowwallet.sparrow.soroban;

import com.google.common.eventbus.Subscribe;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.FollowPayNymEvent;
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

public class PayNymController extends SorobanController {
    private static final Logger log = LoggerFactory.getLogger(PayNymController.class);

    private String walletId;
    private PayNym walletPayNym;

    @FXML
    private CopyableTextField payNymName;

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
            return new PayNymCell(walletId);
        });

        followingList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, payNym) -> {
            payNymProperty.set(payNym);
        });

        followersList.setCellFactory(param -> {
            return new PayNymCell(walletId);
        });

        followersList.setSelectionModel(new NoSelectionModel<>());
        followersList.setFocusTraversable(false);

        refresh();
    }

    private void refresh() {
        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        if(soroban.getPaymentCode() == null) {
            throw new IllegalStateException("Payment code has not been set");
        }

        soroban.getPayNym(soroban.getPaymentCode().toString()).subscribe(payNym -> {
            walletPayNym = payNym;
            payNymName.setText(payNym.nymName());
            paymentCode.setPaymentCode(payNym.paymentCode());
            payNymAvatar.setPaymentCode(payNym.paymentCode());
            followingList.setUserData(null);
            followingList.setItems(FXCollections.observableList(payNym.following()));
            followersList.setItems(FXCollections.observableList(payNym.followers()));
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

    public PayNym getPayNym() {
        return payNymProperty.get();
    }

    public ObjectProperty<PayNym> payNymProperty() {
        return payNymProperty;
    }

    @Subscribe
    public void followPayNym(FollowPayNymEvent event) {
        if(event.getWalletId().equals(walletId)) {
            Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
            soroban.getAuthToken(new HashMap<>()).subscribe(authToken -> {
                String signature = soroban.getSignature(authToken);
                soroban.followPaymentCode(event.getPaymentCode(), authToken, signature).subscribe(followMap -> {
                    refresh();
                }, error -> {
                    log.error("Could not follow payment code", error);
                    AppServices.showErrorDialog("Could not follow payment code", error.getMessage());
                });
            });
        }
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
