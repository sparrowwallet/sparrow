package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.InteractionServices;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.text.BreakIterator;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public class TerminalInteractionServices implements InteractionServices {
    private final Object alertShowing = new Object();
    private final Object passphraseShowing = new Object();

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ButtonType> showAlert(String title, String content, Alert.AlertType alertType, Node graphic, ButtonType... buttons) {
        if(Platform.isFxApplicationThread()) {
            SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                Optional<ButtonType> optButtonType = showMessageDialog(title, content, buttons);
                Platform.runLater(() -> Platform.exitNestedEventLoop(alertShowing, optButtonType));
            });
            return (Optional<ButtonType>)Platform.enterNestedEventLoop(alertShowing);
        } else {
            return showMessageDialog(title, content, buttons);
        }
    }

    private Optional<ButtonType> showMessageDialog(String title, String content, ButtonType[] buttons) {
        String formattedContent = formatLines(content, 50);

        MessageDialogBuilder builder = new MessageDialogBuilder().setTitle(title).setText("\n" + formattedContent);
        for(ButtonType buttonType : buttons) {
            builder.addButton(getButton(buttonType));
        }

        MessageDialogButton button = builder.build().showDialog(SparrowTerminal.get().getGui());
        return Arrays.stream(buttons).filter(buttonType -> button.equals(getButton(buttonType))).findFirst();
    }

    private String formatLines(String input, int maxLength) {
        StringBuilder builder = new StringBuilder();
        BreakIterator boundary = BreakIterator.getLineInstance(Locale.ROOT);
        boundary.setText(input);
        int start = boundary.first();
        int end = boundary.next();
        int lineLength = 0;

        while(end != BreakIterator.DONE) {
            String word = input.substring(start,end);
            lineLength = lineLength + word.length();
            if (lineLength >= maxLength) {
                builder.append("\n");
                lineLength = word.length();
            }
            builder.append(word);
            start = end;
            end = boundary.next();
        }

        return builder.toString();
    }

    private MessageDialogButton getButton(ButtonType buttonType) {
        if(ButtonType.OK.equals(buttonType)) {
            return MessageDialogButton.OK;
        }
        if(ButtonType.CANCEL.equals(buttonType)) {
            return MessageDialogButton.Cancel;
        }
        if(ButtonType.YES.equals(buttonType)) {
            return MessageDialogButton.Yes;
        }
        if(ButtonType.NO.equals(buttonType)) {
            return MessageDialogButton.No;
        }
        if(ButtonType.CLOSE.equals(buttonType)) {
            return MessageDialogButton.Close;
        }

        throw new IllegalArgumentException("Cannot find button for " + buttonType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> requestPassphrase(String walletName, Keystore keystore) {
        if(Platform.isFxApplicationThread()) {
            SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                Optional<String> optPassphrase = showPassphraseDialog(walletName, keystore);
                Platform.runLater(() -> Platform.exitNestedEventLoop(passphraseShowing, optPassphrase));
            });
            return (Optional<String>)Platform.enterNestedEventLoop(passphraseShowing);
        } else {
            return showPassphraseDialog(walletName, keystore);
        }
    }

    private Optional<String> showPassphraseDialog(String walletName, Keystore keystore) {
        PassphraseDialog passphraseDialog = new PassphraseDialog(walletName, keystore);
        String passphrase = passphraseDialog.showDialog(SparrowTerminal.get().getGui());
        return passphrase == null ? Optional.empty() : Optional.of(passphrase);
    }
}
