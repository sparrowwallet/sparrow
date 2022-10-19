package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.WalletForm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WalletAccountsDialog extends DialogWindow {
    private final ActionListBox actions;

    public WalletAccountsDialog(String masterWalletId) {
        super(SparrowTerminal.get().getWalletData().get(masterWalletId).getWalletForm().getWallet().getName());

        setHints(List.of(Hint.CENTERED));

        WalletForm masterWalletForm = SparrowTerminal.get().getWalletData().get(masterWalletId).getWalletForm();
        Storage storage = masterWalletForm.getStorage();
        Wallet masterWallet = masterWalletForm.getWallet();

        actions = new ActionListBox();

        List<Wallet> allWallets = new ArrayList<>(masterWallet.getAllWallets());
        Collections.sort(allWallets);
        for(Wallet wallet : allWallets) {
            actions.addItem(wallet.getDisplayName(), () -> {
                close();
                SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                    WalletActionsDialog walletActionsDialog = new WalletActionsDialog(storage.getWalletId(wallet));
                    walletActionsDialog.showDialog(SparrowTerminal.get().getGui());
                });
            });
        }

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(1).setLeftMarginSize(1).setRightMarginSize(1));
        actions.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER, true, false)).addTo(mainPanel);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Cancel", this::onCancel).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));
        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false)).addTo(mainPanel);

        setComponent(mainPanel);
    }

    private void onCancel() {
        close();
    }

    public void setWalletAccount(Wallet wallet) {
        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        actions.setSelectedIndex(masterWallet.getAllWallets().indexOf(wallet));
    }
}
