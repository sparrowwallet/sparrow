package com.sparrowwallet.sparrow.terminal.preferences;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.PublicElectrumServer;

import java.util.List;

public class PublicElectrumDialog extends ServerProxyDialog {
    private final ComboBox<PublicElectrumServer> url;

    public PublicElectrumDialog() {
        super("Public Electrum");

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel(new GridLayout(3).setHorizontalSpacing(2).setVerticalSpacing(0));
        mainPanel.addComponent(new Label("Warning!"));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new Label("Using a public server means it can see your transactions"),
                GridLayout.createLayoutData(GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER,true,false, 3, 1));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("URL"));
        url = new ComboBox<>();
        for(PublicElectrumServer server : PublicElectrumServer.getServers()) {
            url.addItem(server);
        }
        if(Config.get().getPublicElectrumServer() == null) {
            Config.get().changePublicServer();
        }
        url.setSelectedItem(PublicElectrumServer.fromServer(Config.get().getPublicElectrumServer()));
        url.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            if(selectedIndex != previousSelection) {
                Config.get().setPublicElectrumServer(PublicElectrumServer.getServers().get(selectedIndex).getServer());
            }
        });
        mainPanel.addComponent(url);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        addProxyComponents(mainPanel);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Test", this::onTest));
        buttonPanel.addComponent(new Button("Done", this::onDone));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }
}
