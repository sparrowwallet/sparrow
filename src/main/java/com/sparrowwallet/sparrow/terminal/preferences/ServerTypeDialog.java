package com.sparrowwallet.sparrow.terminal.preferences;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;

import java.util.List;

public class ServerTypeDialog extends DialogWindow {
    private final RadioBoxList<String> type;

    public ServerTypeDialog() {
        super("Server Type");

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5));

        ServerType[] serverTypes = new ServerType[] { ServerType.PUBLIC_ELECTRUM_SERVER, ServerType.BITCOIN_CORE, ServerType.ELECTRUM_SERVER };

        mainPanel.addComponent(new Label("Connect using"));
        type = new RadioBoxList<>();
        for(ServerType serverType : serverTypes) {
            type.addItem(serverType.getName());
        }

        if(Config.get().getServerType() == null) {
            Config.get().setServerType(ServerType.PUBLIC_ELECTRUM_SERVER);
        }
        type.setCheckedItem(Config.get().getServerType().getName());
        type.addListener((selectedIndex, previousSelection) -> {
            if(selectedIndex != previousSelection) {
                Config.get().setServerType(serverTypes[selectedIndex]);
            }
        });
        mainPanel.addComponent(type);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(1).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Continue", this::onContinue));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    private void onContinue() {
        close();

        if(Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER) {
            PublicElectrumDialog publicElectrumServer = new PublicElectrumDialog();
            publicElectrumServer.showDialog(SparrowTerminal.get().getGui());
        } else if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
            BitcoinCoreDialog bitcoinCoreDialog = new BitcoinCoreDialog();
            bitcoinCoreDialog.showDialog(SparrowTerminal.get().getGui());
        } else if(Config.get().getServerType() == ServerType.ELECTRUM_SERVER) {
            PrivateElectrumDialog privateElectrumDialog = new PrivateElectrumDialog();
            privateElectrumDialog.showDialog(SparrowTerminal.get().getGui());
        }
    }
}
