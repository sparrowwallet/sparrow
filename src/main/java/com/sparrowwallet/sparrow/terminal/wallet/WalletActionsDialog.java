package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.Function;

import java.util.List;

public class WalletActionsDialog extends DialogWindow {
    private final String walletId;
    private final ActionListBox actions;

    public WalletActionsDialog(String walletId) {
        super(SparrowTerminal.get().getWalletData().get(walletId).getWalletForm().getWallet().getFullDisplayName());

        setHints(List.of(Hint.CENTERED));

        this.walletId = walletId;

        actions = new ActionListBox();
        actions.addItem("Transactions", () -> {
            close();
            TransactionsDialog transactionsDialog = getWalletData().getTransactionsDialog();
            transactionsDialog.showDialog(SparrowTerminal.get().getGui());
        });
        if(!getWalletData().getWalletForm().getWallet().isWhirlpoolChildWallet()) {
            actions.addItem("Receive", () -> {
                close();
                ReceiveDialog receiveDialog = getWalletData().getReceiveDialog();
                receiveDialog.showDialog(SparrowTerminal.get().getGui());
            });
        }
        actions.addItem("Addresses", () -> {
            close();
            AddressesDialog addressesDialog = getWalletData().getAddressesDialog();
            addressesDialog.showDialog(SparrowTerminal.get().getGui());
        });
        actions.addItem("UTXOs", () -> {
            close();
            UtxosDialog utxosDialog = getWalletData().getUtxosDialog();
            utxosDialog.showDialog(SparrowTerminal.get().getGui());
        });
        actions.addItem("Settings", () -> {
            close();
            SettingsDialog settingsDialog = getWalletData().getSettingsDialog();
            settingsDialog.showDialog(SparrowTerminal.get().getGui());
        });
        if(getWalletData().getWalletForm().getWallet().isEncrypted()) {
            actions.addItem("Lock", () -> {
                close();
                SparrowTerminal.get().lockWallet(getWalletData().getWalletForm().getStorage());
            });
        }

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(1).setLeftMarginSize(1).setRightMarginSize(1));
        actions.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER, true, false)).addTo(mainPanel);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        Wallet masterWallet = getWallet().isMasterWallet() ? getWallet() : getWallet().getMasterWallet();
        if(masterWallet.getChildWallets().stream().anyMatch(childWallet -> !childWallet.isNested())) {
            buttonPanel.addComponent(new Button("Accounts", this::onAccounts).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));
        }
        buttonPanel.addComponent(new Button("Cancel", this::onCancel).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));
        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false)).addTo(mainPanel);

        setComponent(mainPanel);
    }

    public void setFunction(Function function) {
        int isWhirlpoolWallet = getWalletData().getWalletForm().getWallet().isWhirlpoolChildWallet() ? 1 : 0;
        if(function == Function.TRANSACTIONS) {
            actions.setSelectedIndex(0);
        } else if(function == Function.RECEIVE) {
            actions.setSelectedIndex(1);
        } else if(function == Function.ADDRESSES) {
            actions.setSelectedIndex(2 - isWhirlpoolWallet);
        } else if(function == Function.UTXOS) {
            actions.setSelectedIndex(3 - isWhirlpoolWallet);
        }
    }

    private void onCancel() {
        close();
    }

    private void onAccounts() {
        close();
        WalletAccountsDialog walletAccountsDialog = new WalletAccountsDialog(getWalletData().getWalletForm().getMasterWalletId());
        walletAccountsDialog.setWalletAccount(getWallet());
        walletAccountsDialog.showDialog(SparrowTerminal.get().getGui());
    }

    private WalletData getWalletData() {
        WalletData walletData = SparrowTerminal.get().getWalletData().get(walletId);
        if(walletData == null) {
            throw new IllegalStateException("Wallet data is null for " + walletId);
        }

        return walletData;
    }

    private Wallet getWallet() {
        return getWalletData().getWalletForm().getWallet();
    }
}
