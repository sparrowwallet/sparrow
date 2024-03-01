package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.whirlpool.WhirlpoolServices;

import java.util.ArrayList;
import java.util.List;

final class AddAccountDialog extends DialogWindow {
    private static final int MAX_SHOWN_ACCOUNTS = 8;

    private ComboBox<DisplayStandardAccount> standardAccounts;
    private StandardAccount standardAccount;

    public AddAccountDialog(Wallet wallet) {
        super("Add Account");

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5).setVerticalSpacing(1));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("Account to add"));
        standardAccounts = new ComboBox<>();
        mainPanel.addComponent(standardAccounts);

        List<Integer> existingIndexes = new ArrayList<>();
        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        existingIndexes.add(masterWallet.getAccountIndex());
        for(Wallet childWallet : masterWallet.getChildWallets()) {
            if(!childWallet.isNested()) {
                existingIndexes.add(childWallet.getAccountIndex());
            }
        }

        List<StandardAccount> availableAccounts = new ArrayList<>();
        for(StandardAccount standardAccount : StandardAccount.values()) {
            if(!existingIndexes.contains(standardAccount.getAccountNumber()) && !StandardAccount.isWhirlpoolAccount(standardAccount) && availableAccounts.size() <= MAX_SHOWN_ACCOUNTS) {
                availableAccounts.add(standardAccount);
            }
        }

        if(WhirlpoolServices.canWalletMix(masterWallet) && !masterWallet.isWhirlpoolMasterWallet()) {
            availableAccounts.add(StandardAccount.WHIRLPOOL_PREMIX);
        }

        availableAccounts.stream().map(DisplayStandardAccount::new).forEach(standardAccounts::addItem);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Cancel", this::onCancel));
        Button okButton = new Button("Add Account", this::addAccount).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false));
        buttonPanel.addComponent(okButton);

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    private void addAccount() {
        standardAccount = standardAccounts.getSelectedItem().account;
        close();
    }

    private void onCancel() {
        close();
    }

    @Override
    public StandardAccount showDialog(WindowBasedTextGUI textGUI) {
        super.showDialog(textGUI);
        return standardAccount;
    }

    private static class DisplayStandardAccount {
        private final StandardAccount account;

        public DisplayStandardAccount(StandardAccount standardAccount) {
            this.account = standardAccount;
        }

        @Override
        public String toString() {
            if(StandardAccount.isWhirlpoolAccount(account)) {
                return "Whirlpool Accounts";
            }

            return account.getName();
        }
    }
}
