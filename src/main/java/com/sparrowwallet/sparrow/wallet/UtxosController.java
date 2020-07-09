package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.UtxosChart;
import com.sparrowwallet.sparrow.control.UtxosTreeTable;
import com.sparrowwallet.sparrow.event.BitcoinUnitChangedEvent;
import com.sparrowwallet.sparrow.event.WalletEntryLabelChangedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.event.WalletNodesChangedEvent;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class UtxosController extends WalletFormController implements Initializable {

    @FXML
    private UtxosTreeTable utxosTable;

    @FXML
    private UtxosChart utxosChart;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        utxosTable.initialize(getWalletForm().getWalletUtxosEntry());
        utxosChart.initialize(getWalletForm().getWalletUtxosEntry());

        utxosTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            List<Entry> selectedEntries = utxosTable.getSelectionModel().getSelectedCells().stream().map(tp -> tp.getTreeItem().getValue()).collect(Collectors.toList());
            utxosChart.select(selectedEntries);
        });
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            utxosTable.updateAll(getWalletForm().getWalletUtxosEntry());
            utxosChart.update(getWalletForm().getWalletUtxosEntry());
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            utxosTable.updateHistory(event.getHistoryChangedNodes());
            utxosChart.update(getWalletForm().getWalletUtxosEntry());
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            utxosTable.updateLabel(event.getEntry());
            utxosChart.update(getWalletForm().getWalletUtxosEntry());
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        utxosTable.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
        utxosChart.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
    }
}
