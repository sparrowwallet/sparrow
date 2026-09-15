package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import javafx.scene.control.ButtonType;

import java.util.List;

/**
 * Shows an alert whose buttons carry text of their own, which Lanterna's MessageDialog cannot do - its buttons are a fixed enum of OK, Cancel, Yes and
 * so on. Mapping a button such as "Refresh Wallet" onto one of those loses the only description of what it does, and the role it declares is no
 * substitute: the wallet refresh is a CANCEL_CLOSE button, so it would be offered as Cancel while doing the opposite of cancelling.
 * <p>
 * The button that was pressed is returned as the instance the caller supplied, since callers identify their own buttons by identity.
 */
public class ButtonTypeDialog extends DialogWindow {
    private ButtonType result;

    public ButtonTypeDialog(String title, String content, ButtonType[] buttonTypes) {
        super(title);

        setHints(List.of(Hint.CENTERED));

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(buttonTypes.length).setHorizontalSpacing(1));
        for(ButtonType buttonType : buttonTypes) {
            buttonPanel.addComponent(new Button(buttonType.getText(), () -> {
                result = buttonType;
                close();
            }));
        }

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(1).setLeftMarginSize(1).setRightMarginSize(1));
        mainPanel.addComponent(new Label("\n" + content));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, false, false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    @Override
    public ButtonType showDialog(WindowBasedTextGUI textGUI) {
        super.showDialog(textGUI);
        return result;
    }
}
