package com.sparrowwallet.sparrow.terminal.preferences;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.net.Protocol;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;

import java.io.File;
import java.util.List;

public class PrivateElectrumDialog extends ServerUrlDialog {
    private final ComboBox<String> useSsl;
    private final TextBox certificate;
    private final Button selectCertificate;

    public PrivateElectrumDialog() {
        super("Private Electrum");

        setHints(List.of(Hint.CENTERED));
        Panel mainPanel = new Panel(new GridLayout(3).setHorizontalSpacing(2).setVerticalSpacing(0));

        if(Config.get().getElectrumServer() == null) {
            Config.get().setElectrumServer(new Server(Protocol.TCP.toUrlString("127.0.0.1", Protocol.TCP.getDefaultPort())));
        }
        addUrlComponents(mainPanel, Config.get().getRecentElectrumServers(), Config.get().getElectrumServer());
        addLine(mainPanel);

        mainPanel.addComponent(new Label("Use SSL?"));
        useSsl = new ComboBox<>("Yes", "No");
        useSsl.setSelectedIndex(1);
        mainPanel.addComponent(useSsl);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("Certificate"));
        certificate = new TextBox(new TerminalSize(30, 1), Config.get().getElectrumServerCert() != null ? Config.get().getElectrumServerCert().getAbsolutePath() : "");
        mainPanel.addComponent(certificate);
        selectCertificate = new Button("Select...");
        mainPanel.addComponent(selectCertificate);

        Server configuredServer = Config.get().getElectrumServer();
        if(configuredServer != null) {
            Protocol protocol = configuredServer.getProtocol();
            boolean ssl = protocol.equals(Protocol.SSL);
            useSsl.setSelectedIndex(ssl ? 0 : 1);
            certificate.setEnabled(ssl);
            selectCertificate.setEnabled(ssl);
        }

        useSsl.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            setServerConfig();
            certificate.setEnabled(selectedIndex == 0);
            selectCertificate.setEnabled(selectedIndex == 0);
        });
        certificate.setTextChangeListener((newText, changedByUserInteraction) -> {
            File crtFile = getCertificate(newText);
            Config.get().setElectrumServerCert(crtFile);
        });
        selectCertificate.addListener(button -> {
            FileDialogBuilder builder = new FileDialogBuilder().setTitle("Select SSL Certificate").setActionLabel("Select");
            builder.setShowHiddenDirectories(true);
            File file = builder.build().showDialog(SparrowTerminal.get().getGui());
            if(file != null && getCertificate(file.getAbsolutePath()) != null) {
                certificate.setText(file.getAbsolutePath());
            }
        });

        addLine(mainPanel);
        addProxyComponents(mainPanel);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Test", this::onTest));
        buttonPanel.addComponent(new Button("Done", this::onDone));

        addLine(mainPanel);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    protected void setServerConfig() {
        Server currentServer = getCurrentServer();
        if(currentServer != null) {
            Config.get().setElectrumServer(currentServer);
        }
    }

    protected void setServerAlias(Server server) {
        Config.get().setElectrumServerAlias(server);
    }

    protected Protocol getProtocol() {
        return (useSsl.getSelectedIndex() == 0 ? Protocol.SSL : Protocol.TCP);
    }

    protected void setProtocol(Protocol protocol) {
        useSsl.setSelectedIndex(protocol == Protocol.SSL ? 0 : 1);
    }
}
