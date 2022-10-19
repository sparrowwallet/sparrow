package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;

import java.util.List;

public final class ModalDialog extends DialogWindow {
    public ModalDialog(String walletName, String description) {
        super(walletName);

        setHints(List.of(Hint.CENTERED));
        setFixedSize(new TerminalSize(30, 5));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new LinearLayout());
        mainPanel.addComponent(new EmptySpace(), LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning, LinearLayout.GrowPolicy.CanGrow));

        Label label = new Label(description);
        mainPanel.addComponent(label, LinearLayout.createLayoutData(LinearLayout.Alignment.Center));

        mainPanel.addComponent(new EmptySpace(), LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning, LinearLayout.GrowPolicy.CanGrow));

        setComponent(mainPanel);
    }
}
