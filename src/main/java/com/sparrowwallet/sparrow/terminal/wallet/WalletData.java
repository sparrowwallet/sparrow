package com.sparrowwallet.sparrow.terminal.wallet;

import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.wallet.WalletForm;

public class WalletData {
    private final WalletForm walletForm;
    private TransactionsDialog transactionsDialog;
    private ReceiveDialog receiveDialog;
    private AddressesDialog addressesDialog;
    private UtxosDialog utxosDialog;

    public WalletData(WalletForm walletForm) {
        this.walletForm = walletForm;
    }

    public WalletForm getWalletForm() {
        return walletForm;
    }

    public TransactionsDialog getTransactionsDialog() {
        if(transactionsDialog == null) {
            transactionsDialog = new TransactionsDialog(walletForm);
            EventManager.get().register(transactionsDialog);
        }

        return transactionsDialog;
    }

    public ReceiveDialog getReceiveDialog() {
        if(receiveDialog == null) {
            receiveDialog = new ReceiveDialog(walletForm);
            EventManager.get().register(receiveDialog);
        }

        return receiveDialog;
    }

    public AddressesDialog getAddressesDialog() {
        if(addressesDialog == null) {
            addressesDialog = new AddressesDialog(walletForm);
            EventManager.get().register(addressesDialog);
        }

        return addressesDialog;
    }

    public UtxosDialog getUtxosDialog() {
        if(utxosDialog == null) {
            utxosDialog = new UtxosDialog(walletForm);
            EventManager.get().register(utxosDialog);
        }

        return utxosDialog;
    }
}
