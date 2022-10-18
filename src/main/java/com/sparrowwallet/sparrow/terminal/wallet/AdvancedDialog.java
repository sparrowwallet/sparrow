package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.wallet.WalletForm;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class AdvancedDialog extends WalletDialog {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final TextBox birthDate;
    private final TextBox gapLimit;
    private final Button apply;

    private Result result = Result.CANCEL;

    public AdvancedDialog(WalletForm walletForm) {
        super(walletForm.getWallet().getFullDisplayName() + " Advanced Settings", walletForm);

        setHints(List.of(Hint.CENTERED));

        Wallet wallet = getWalletForm().getWallet();

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5).setVerticalSpacing(1));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("Birth date"));
        birthDate = new TextBox().setValidationPattern(Pattern.compile("[0-9\\-/]*"));
        mainPanel.addComponent(birthDate);

        mainPanel.addComponent(new Label("Gap limit"));
        gapLimit = new TextBox().setValidationPattern(Pattern.compile("[0-9]*"));
        mainPanel.addComponent(gapLimit);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Cancel", this::onCancel));
        apply = new Button("Apply", this::onApply).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false));
        apply.setEnabled(false);
        buttonPanel.addComponent(apply);

        boolean noPassword = Storage.NO_PASSWORD_KEY.equals(walletForm.getStorage().getEncryptionPubKey());
        mainPanel.addComponent(new Button(noPassword ? "Add Password" : "Change Password", this::onChangePassword));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);

        if(wallet.getBirthDate() != null) {
            birthDate.setText(DATE_FORMAT.format(wallet.getBirthDate()));
        }

        gapLimit.setText(Integer.toString(wallet.getGapLimit()));

        birthDate.setTextChangeListener((newText, changedByUserInteraction) -> {
            try {
                Date newDate = DATE_FORMAT.parse(newText);
                wallet.setBirthDate(newDate);
                apply.setEnabled(true);
            } catch(ParseException e) {
                //ignore
            }
        });

        gapLimit.setTextChangeListener((newText, changedByUserInteraction) -> {
            try {
                int newValue = Integer.parseInt(newText);
                if(newValue < 0 || newValue > 1000000) {
                    return;
                }

                wallet.setGapLimit(newValue);
                apply.setEnabled(true);
            } catch(NumberFormatException e) {
                return;
            }
        });
    }

    private void onChangePassword() {
        result = Result.CHANGE_PASSWORD;
        close();
    }

    private void onApply() {
        result = Result.APPLY;
        close();
    }

    private void onCancel() {
        close();
    }

    @Override
    public Object showDialog(WindowBasedTextGUI textGUI) {
        super.showDialog(textGUI);
        return result;
    }

    public enum Result {
        CANCEL, APPLY, CHANGE_PASSWORD
    }
}
