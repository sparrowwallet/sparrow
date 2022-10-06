package com.sparrowwallet.sparrow.terminal.preferences;

import com.google.common.net.HostAndPort;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.Mode;
import com.sparrowwallet.sparrow.event.RequestConnectEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import javafx.application.Platform;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.util.regex.Pattern;

public abstract class ServerProxyDialog extends DialogWindow {
    private ComboBox<String> useProxy;
    private TextBox proxyHost;
    private TextBox proxyPort;

    public ServerProxyDialog(String title) {
        super(title);
    }

    protected void onDone() {
        close();

        Platform.runLater(() -> {
            if(Config.get().getMode() == Mode.ONLINE && !(AppServices.isConnecting() || AppServices.isConnected())) {
                EventManager.get().post(new RequestConnectEvent());
            }
        });
    }

    protected void onTest() {
        close();

        ServerTestDialog serverTestDialog = new ServerTestDialog();
        serverTestDialog.showDialog(SparrowTerminal.get().getGui());
    }

    protected void addProxyComponents(Panel mainPanel) {
        mainPanel.addComponent(new Label("Use Proxy?"));
        useProxy = new ComboBox<>("Yes", "No");
        useProxy.setSelectedIndex(Config.get().isUseProxy() ? 0 : 1);
        useProxy.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            Config.get().setUseProxy(selectedIndex == 0);
        });
        mainPanel.addComponent(useProxy);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("Proxy URL"));
        proxyHost = new TextBox(new TerminalSize(30,1)).setValidationPattern(Pattern.compile("[a-zA-Z0-9.]+"));
        mainPanel.addComponent(proxyHost);

        proxyPort = new TextBox(new TerminalSize(6,1)).setValidationPattern(Pattern.compile("[0-9]*"));
        mainPanel.addComponent(proxyPort);

        String proxyServer = Config.get().getProxyServer();
        if(proxyServer != null) {
            HostAndPort server = HostAndPort.fromString(proxyServer);
            proxyHost.setText(server.getHost());
            if(server.hasPort()) {
                proxyPort.setText(Integer.toString(server.getPort()));
            }
        }

        proxyHost.setTextChangeListener((newText, changedByUserInteraction) -> {
            setProxyConfig();
        });
        proxyPort.setTextChangeListener((newText, changedByUserInteraction) -> {
            setProxyConfig();
        });
    }

    private void setProxyConfig() {
        String hostAsString = getHost(proxyHost.getText());
        Integer portAsInteger = getPort(proxyPort.getText());
        if(hostAsString != null && portAsInteger != null && isValidPort(portAsInteger)) {
            Config.get().setProxyServer(HostAndPort.fromParts(hostAsString, portAsInteger).toString());
        } else if(hostAsString != null) {
            Config.get().setProxyServer(HostAndPort.fromHost(hostAsString).toString());
        }
    }

    protected String getHost(String text) {
        try {
            return HostAndPort.fromHost(text).getHost();
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

    protected Integer getPort(String text) {
        try {
            return Integer.parseInt(text);
        } catch(NumberFormatException e) {
            return null;
        }
    }

    protected static boolean isValidPort(int port) {
        return port >= 0 && port <= 65535;
    }

    protected File getCertificate(String crtFileLocation) {
        try {
            File crtFile = new File(crtFileLocation);
            if(!crtFile.exists()) {
                return null;
            }

            CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));
            return crtFile;
        } catch (Exception e) {
            return null;
        }
    }

    protected void addLine(Panel mainPanel) {
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
    }
}
