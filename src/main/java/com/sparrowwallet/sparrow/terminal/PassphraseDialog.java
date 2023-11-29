package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;

import java.util.Collections;

public class PassphraseDialog extends DialogWindow {
    private final Label masterFingerprint;
    private final TextBox passphrase;
    private String result;

    public PassphraseDialog(String walletName, Keystore keystore) {
        super("Passphrase for " + walletName);

        setHints(Collections.singleton(Window.Hint.CENTERED));

        this.masterFingerprint = new Label("");
        this.passphrase = new TextBox();
        this.passphrase.setMask('*');

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button(LocalizedString.OK.toString(), this::onOK).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));
        buttonPanel.addComponent(new Button(LocalizedString.Cancel.toString(), this::onCancel));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(1).setLeftMarginSize(1).setRightMarginSize(1));
        mainPanel.addComponent(new Label("Enter the BIP39 passphrase for keystore:\n" + keystore.getLabel()));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        passphrase.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.FILL, GridLayout.Alignment.CENTER, true, false)).addTo(mainPanel);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(masterFingerprint);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false)).addTo(mainPanel);
        setComponent(mainPanel);

        passphrase.setTextChangeListener((newText, changedByUserInteraction) -> {
            setMasterFingerprintLabel(keystore, newText);
        });
        setMasterFingerprintLabel(keystore, "");
    }

    private void onOK() {
        result = passphrase.getText();
        close();
    }

    private void onCancel() {
        close();
    }

    @Override
    public String showDialog(WindowBasedTextGUI textGUI) {
        result = null;
        super.showDialog(textGUI);
        return result;
    }

    private void setMasterFingerprintLabel(Keystore keystore, String passphrase) {
        masterFingerprint.setText("Master fingerprint: " + Utils.bytesToHex(getMasterFingerprint(keystore, passphrase)));
    }

    private byte[] getMasterFingerprint(Keystore keystore, String passphrase) {
        try {
            Keystore copyKeystore = keystore.copy();
            copyKeystore.getSeed().setPassphrase(passphrase);
            return copyKeystore.getExtendedMasterPrivateKey().getKey().getFingerprint();
        } catch(MnemonicException e) {
            throw new RuntimeException(e);
        }
    }
}
