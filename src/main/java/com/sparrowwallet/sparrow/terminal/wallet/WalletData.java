package com.sparrowwallet.sparrow.terminal.wallet;

import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.wallet.SettingsWalletForm;
import com.sparrowwallet.sparrow.wallet.WalletForm;

public class WalletData {
    private final WalletForm walletForm;
    private TransactionsDialog transactionsDialog;
    private ReceiveDialog receiveDialog;
    private AddressesDialog addressesDialog;
    private UtxosDialog utxosDialog;
    private SettingsDialog settingsDialog;

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

    public SettingsDialog getSettingsDialog() {
        if(settingsDialog == null) {
            SettingsWalletForm settingsWalletForm = new SettingsWalletForm(walletForm.getStorage(), walletForm.getWallet(), walletForm);
            settingsDialog = new SettingsDialog(settingsWalletForm);
            EventManager.get().register(settingsDialog);
        }

        return settingsDialog;
    }
}
