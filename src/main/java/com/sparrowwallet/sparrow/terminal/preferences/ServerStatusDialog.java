package com.sparrowwallet.sparrow.terminal.preferences;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.Mode;
import com.sparrowwallet.sparrow.event.RequestConnectEvent;
import com.sparrowwallet.sparrow.event.RequestDisconnectEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import javafx.application.Platform;

import java.util.List;

public class ServerStatusDialog extends DialogWindow {
    private final ComboBox<String> connect;

    public ServerStatusDialog() {
        super("Server Preferences");

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5));

        mainPanel.addComponent(new Label("Connect?"));
        connect = new ComboBox<>();
        connect.addItem("Yes");
        connect.addItem("No");
        connect.setSelectedIndex(Config.get().getMode() == Mode.ONLINE ? 0 : 1);
        connect.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            if(selectedIndex != previousSelection) {
                Config.get().setMode(selectedIndex == 0 ? Mode.ONLINE : Mode.OFFLINE);
                Platform.runLater(() -> {
                    EventManager.get().post(selectedIndex == 0 ? new RequestConnectEvent() : new RequestDisconnectEvent());
                });
            }
        });
        mainPanel.addComponent(connect);

        mainPanel.addComponent(new Label("Server Type"));
        mainPanel.addComponent(new Label(Config.get().getServerType().getName()));

        mainPanel.addComponent(new Label("Server"));
        mainPanel.addComponent(new Label(Config.get().getServer().getDisplayName()));

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Edit", this::onEdit).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));
        buttonPanel.addComponent(new Button("Cancel", this::onCancel));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    private void onEdit() {
        Platform.runLater(() -> {
            EventManager.get().post(new RequestDisconnectEvent());
        });
        close();

        ServerTypeDialog serverTypeDialog = new ServerTypeDialog();
        serverTypeDialog.showDialog(SparrowTerminal.get().getGui());
    }

    private void onCancel() {
        close();
    }
}
