package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.TransactionsTreeTable;
import com.sparrowwallet.sparrow.event.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.util.ResourceBundle;

public class TransactionsController extends WalletFormController implements Initializable {

    @FXML
    private TransactionsTreeTable transactionsTable;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        transactionsTable.initialize(getWalletForm().getWalletTransactionsEntry());
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            transactionsTable.updateAll(getWalletForm().getWalletTransactionsEntry());
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            transactionsTable.updateHistory(event.getHistoryChangedNodes());
        }
    }

    @Subscribe
    public void walletEntryLabelChanged(WalletEntryLabelChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            transactionsTable.updateLabel(event.getEntry());
        }
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        transactionsTable.setBitcoinUnit(getWalletForm().getWallet(), event.getBitcoinUnit());
    }

    //TODO: Remove
    public void advanceBlock(MouseEvent event) {
        Integer currentBlock = getWalletForm().getWallet().getStoredBlockHeight();
        getWalletForm().getWallet().setStoredBlockHeight(currentBlock+1);
        System.out.println("Advancing from " + currentBlock + " to " + getWalletForm().getWallet().getStoredBlockHeight());
        EventManager.get().post(new WalletBlockHeightChangedEvent(getWalletForm().getWallet(), currentBlock+1));
    }
}
