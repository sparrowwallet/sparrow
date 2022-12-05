package com.sparrowwallet.sparrow.terminal.preferences;

import com.google.common.net.HostAndPort;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.net.Protocol;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class ServerUrlDialog extends ServerProxyDialog {
    private ComboBox<ServerItem> host;
    private TextBox port;
    private TextBox alias;

    public ServerUrlDialog(String title) {
        super(title);
    }

    protected void addUrlComponents(Panel mainPanel, List<Server> recentServers, Server configuredServer) {
        mainPanel.addComponent(new Label("URL"));
        host = new ComboBox<>();
        host.setPreferredSize(new TerminalSize(30,1));
        host.setReadOnly(false);
        mainPanel.addComponent(host, GridLayout.createLayoutData(GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER, true, false));
        port = new TextBox(new TerminalSize(6,1)).setValidationPattern(Pattern.compile("[0-9]*"));
        mainPanel.addComponent(port);

        mainPanel.addComponent(new Label("Alias (optional)"));
        alias = new TextBox(new TerminalSize(30,1));
        mainPanel.addComponent(alias);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        if(configuredServer != null) {
            HostAndPort hostAndPort = configuredServer.getHostAndPort();
            recentServers.stream().map(ServerItem::new).forEach(host::addItem);
            host.setSelectedItem(new ServerItem(configuredServer));
            if(host.getItemCount() == 0) {
                host.addItem(new ServerItem(configuredServer));
            }
            if(hostAndPort.hasPort()) {
                port.setText(Integer.toString(hostAndPort.getPort()));
            }

            if(configuredServer.getAlias() != null) {
                alias.setText(configuredServer.getAlias());
            }
        }

        host.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            Optional<Server> optServer = recentServers.stream().filter(server -> server.equals(host.getSelectedItem().getServer())).findFirst();
            if(optServer.isPresent()) {
                Server server = optServer.get();
                port.setText(server.getHostAndPort().hasPort() ? Integer.toString(server.getHostAndPort().getPort()) : "");
                alias.setText(server.getAlias() == null ? "" : server.getAlias());
                setProtocol(server.getProtocol());
            }
            setServerConfig();
        });
        port.setTextChangeListener((newText, changedByUserInteraction) -> {
            setServerConfig();
        });
        alias.setTextChangeListener((newText, changedByUserInteraction) -> {
            Server currentServer = getCurrentServer();
            if(currentServer != null && host.getSelectedItem() != null && currentServer.equals(host.getSelectedItem().getServer())) {
                setServerAlias(currentServer);
            }
            setServerConfig();
        });
    }

    @Override
    protected void onDone() {
        setServerConfig();
        super.onDone();
    }

    @Override
    protected void onTest() {
        setServerConfig();
        super.onTest();
    }

    protected abstract void setServerConfig();

    protected abstract void setServerAlias(Server server);

    protected abstract Protocol getProtocol();

    protected abstract void setProtocol(Protocol protocol);

    protected Server getCurrentServer() {
        String hostAsString = getHost(host.getText());
        Integer portAsInteger = getPort(port.getText());
        if(hostAsString != null && portAsInteger != null && isValidPort(portAsInteger)) {
            return new Server(getProtocol().toUrlString(hostAsString, portAsInteger), getAlias());
        } else if(hostAsString != null) {
            return new Server(getProtocol().toUrlString(hostAsString), getAlias());
        }

        return null;
    }

    protected Integer getServerPort() {
        return getPort(port.getText());
    }

    private String getAlias() {
        return alias.getText().isEmpty() ? null : alias.getText();
    }

    protected static class ServerItem {
        private final Server server;

        public ServerItem(Server server) {
            this.server = server;
        }

        public Server getServer() {
            return server;
        }

        @Override
        public String toString() {
            return server.getHost();
        }
    }
}
