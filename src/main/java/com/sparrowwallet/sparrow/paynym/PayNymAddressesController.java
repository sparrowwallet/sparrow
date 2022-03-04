package com.sparrowwallet.sparrow.paynym;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.control.AddressTreeTable;
import com.sparrowwallet.sparrow.event.WalletEntryLabelsChangedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PayNymAddressesController {

    @FXML
    private ComboBox<WalletForm> payNymWalletForms;

    @FXML
    private AddressTreeTable receiveTable;

    @FXML
    private AddressTreeTable sendTable;

    public void initializeView(WalletForm walletForm) {
        payNymWalletForms.setItems(FXCollections.observableList(walletForm.getNestedWalletForms().stream().filter(nested -> nested.getWallet().isBip47()).collect(Collectors.toList())));
        payNymWalletForms.setConverter(new StringConverter<>() {
            @Override
            public String toString(WalletForm nested) {
                return nested == null ? "" : nested.getWallet().getDisplayName();
            }

            @Override
            public WalletForm fromString(String string) {
                return null;
            }
        });

        Optional<WalletForm> optInitial = walletForm.getNestedWalletForms().stream().filter(nested -> nested.getWallet().isBip47() && nested.getWallet().getScriptType() == ScriptType.P2WPKH).findFirst();
        if(optInitial.isPresent()) {
            optInitial.get().getAccountEntries().clear();
            receiveTable.initialize(optInitial.get().getNodeEntry(KeyPurpose.RECEIVE));
            sendTable.initialize(optInitial.get().getNodeEntry(KeyPurpose.SEND));
            payNymWalletForms.getSelectionModel().select(optInitial.get());
        }

        payNymWalletForms.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            selected.getAccountEntries().clear();
            receiveTable.updateAll(selected.getNodeEntry(KeyPurpose.RECEIVE));
            sendTable.updateAll(selected.getNodeEntry(KeyPurpose.SEND));
        });
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(payNymWalletForms.getValue().getWallet())) {
            List<WalletNode> receiveNodes = event.getReceiveNodes();
            if(!receiveNodes.isEmpty()) {
                receiveTable.updateHistory(receiveNodes);
            }

            List<WalletNode> sendNodes = event.getChangeNodes();
            if(!sendNodes.isEmpty()) {
                sendTable.updateHistory(sendNodes);
            }
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelsChangedEvent event) {
        if(event.getWallet().equals(payNymWalletForms.getValue().getWallet())) {
            for(Entry entry : event.getEntries()) {
                receiveTable.updateLabel(entry);
                sendTable.updateLabel(entry);
            }
        }
    }
}
