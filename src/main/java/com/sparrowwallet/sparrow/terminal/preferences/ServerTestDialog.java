package com.sparrowwallet.sparrow.terminal.preferences;

import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.Mode;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.*;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.util.Duration;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;

public class ServerTestDialog extends DialogWindow {
    private final Label testStatus;
    private final TextBox testResults;

    private TorService torService;
    private ElectrumServer.ConnectionService connectionService;

    public ServerTestDialog() {
        super("Server Test");

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel(new GridLayout(1));

        this.testStatus = new Label("");
        mainPanel.addComponent(testStatus);

        TerminalSize screenSize = SparrowTerminal.get().getScreen().getTerminalSize();
        int resultsWidth = Math.min(Math.max(20, screenSize.getColumns() - 20), 100);

        this.testResults = new TextBox(new TerminalSize(resultsWidth, 10));
        testResults.setReadOnly(true);
        mainPanel.addComponent(testResults);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(3).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Back", this::onBack).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, false, false)));
        buttonPanel.addComponent(new Button("Test", this::onTest).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));
        Button doneButton = new Button("Done", this::onDone);
        buttonPanel.addComponent(doneButton);
        SparrowTerminal.get().getGuiThread().invokeLater(doneButton::takeFocus);

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);

        EventManager.get().register(this);

        onTest();
    }

    public void onBack() {
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

    public void onTest() {
        testResults.setText("Connecting " + (Config.get().hasServer() ? "to " + Config.get().getServer().getUrl() : "") + "...");

        Platform.runLater(() -> {
            if(Config.get().requiresInternalTor() && Tor.getDefault() == null) {
                startTor();
            } else {
                startElectrumConnection();
            }
        });
    }

    public void onDone() {
        EventManager.get().unregister(this);
        close();

        Platform.runLater(() -> {
            if(connectionService != null && connectionService.isRunning()) {
                connectionService.cancel();
            }
            if(Config.get().getMode() == Mode.ONLINE && !(AppServices.isConnecting() || AppServices.isConnected())) {
                EventManager.get().post(new RequestConnectEvent());
            }
        });
    }

    private void startTor() {
        if(torService != null && torService.isRunning()) {
            return;
        }

        torService = new TorService();
        torService.setPeriod(Duration.hours(1000));
        torService.setRestartOnFailure(false);

        torService.setOnSucceeded(workerStateEvent -> {
            Tor.setDefault(torService.getValue());
            torService.cancel();
            appendText("\nTor running, connecting to " + Config.get().getServer().getUrl() + "...");
            startElectrumConnection();
        });
        torService.setOnFailed(workerStateEvent -> {
            torService.cancel();
            appendText("\nTor failed to start");
            showConnectionFailure(workerStateEvent.getSource().getException());
        });

        torService.start();
    }

    private void startElectrumConnection() {
        if(connectionService != null && connectionService.isRunning()) {
            connectionService.cancel();
        }

        connectionService = new ElectrumServer.ConnectionService(false);
        connectionService.setPeriod(Duration.hours(1));
        connectionService.setRestartOnFailure(false);
        EventManager.get().register(connectionService);

        connectionService.setOnSucceeded(successEvent -> {
            EventManager.get().unregister(connectionService);
            ConnectionEvent connectionEvent = (ConnectionEvent)connectionService.getValue();
            showConnectionSuccess(connectionEvent.getServerVersion(), connectionEvent.getServerBanner());
            Config.get().setMode(Mode.ONLINE);
            connectionService.cancel();
            Config.get().addRecentServer();
        });
        connectionService.setOnFailed(workerStateEvent -> {
            EventManager.get().unregister(connectionService);
            if(connectionService.isShutdown()) {
                connectionService.cancel();
                return;
            }

            showConnectionFailure(workerStateEvent.getSource().getException());
            connectionService.cancel();
        });
        connectionService.start();
    }

    private void appendText(String text) {
        testResults.setText(testResults.getText() + text);
    }

    private void showConnectionSuccess(List<String> serverVersion, String serverBanner) {
        testStatus.setText("Success");
        if(serverVersion != null) {
            testResults.setText("Connected to " + serverVersion.get(0) + " on protocol version " + serverVersion.get(1));
            if(ElectrumServer.supportsBatching(serverVersion)) {
                testResults.setText(testResults.getText() + "\nBatched RPC enabled.");
            }
        }
        if(serverBanner != null) {
            testResults.setText(testResults.getText() + "\nServer Banner: " + serverBanner);
        }
    }

    private void showConnectionFailure(Throwable exception) {
        String reason = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
        if(exception instanceof TlsServerException && exception.getCause() != null) {
            TlsServerException tlsServerException = (TlsServerException)exception;
            if(exception.getCause().getMessage().contains("PKIX path building failed")) {
                File configCrtFile = Config.get().getElectrumServerCert();
                File savedCrtFile = Storage.getCertificateFile(tlsServerException.getServer().getHost());
                if(configCrtFile == null && savedCrtFile != null) {
                    Optional<ButtonType> optButton = AppServices.showErrorDialog("SSL Handshake Failed", "The certificate provided by the server at " + tlsServerException.getServer().getHost() + " appears to have changed." +
                            "\n\nThis may indicate a man-in-the-middle attack!" +
                            "\n\nDo you still want to proceed?", ButtonType.NO, ButtonType.YES);
                    if(optButton.isPresent() && optButton.get() == ButtonType.YES) {
                        if(savedCrtFile.delete()) {
                            Platform.runLater(this::startElectrumConnection);
                            return;
                        } else {
                            AppServices.showErrorDialog("Could not delete certificate", "The certificate file at " + savedCrtFile.getAbsolutePath() + " could not be deleted.\n\nPlease delete this file manually.");
                        }
                    }
                }
            }

            reason = tlsServerException.getMessage() + "\n\n" + reason;
        } else if(exception instanceof ProxyServerException) {
            reason += ". Check if the proxy server is running.";
        } else if(reason != null && reason.contains("Check if Bitcoin Core is running")) {
            reason += "\n\nSee https://sparrowwallet.com/docs/connect-node.html";
        }

        testStatus.setText("Failed");
        testResults.setText("Could not connect:\n\n" + reason);
    }

    @Subscribe
    public void cormorantSyncStatus(CormorantSyncStatusEvent event) {
        if(connectionService != null && connectionService.isRunning() && event.getProgress() < 100) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            appendText("\nThe connection to the Bitcoin Core node was successful, but it is still syncing and cannot be used yet.");
            appendText("\nCurrently " + event.getProgress() + "% completed to date " + dateFormat.format(event.getTip()));
            connectionService.cancel();
        }
    }

    @Subscribe
    public void bwtStatus(BwtStatusEvent event) {
        if(!(event instanceof BwtSyncStatusEvent)) {
            appendText("\n" + event.getStatus());
        }
    }

    @Subscribe
    public void bwtSyncStatus(BwtSyncStatusEvent event) {
        if(connectionService != null && connectionService.isRunning() && event.getProgress() < 100) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            appendText("\nThe connection to the Bitcoin Core node was successful, but it is still syncing and cannot be used yet.");
            appendText("\nCurrently " + event.getProgress() + "% completed to date " + dateFormat.format(event.getTip()));
            connectionService.cancel();
        }
    }

    @Subscribe
    public void torStatus(TorStatusEvent event) {
        Platform.runLater(() -> {
            if(torService != null && torService.isRunning()) {
                appendText("\n" + event.getStatus());
            }
        });
    }
}
