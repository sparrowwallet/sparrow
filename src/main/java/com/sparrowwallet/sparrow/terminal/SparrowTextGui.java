package com.sparrowwallet.sparrow.terminal;

import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.screen.Screen;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ServerType;
import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;

public class SparrowTextGui extends MultiWindowTextGUI {
    private final BasicWindow mainWindow;

    private final Label connectedLabel;
    private final Label statusLabel;
    private final ProgressBar statusProgress;

    private PauseTransition wait;
    private Timeline statusTimeline;
    private final DoubleProperty progressProperty = new SimpleDoubleProperty();

    public SparrowTextGui(SparrowTerminal sparrowTerminal, Screen screen, WindowManager windowManager, Component background) {
        super(screen, windowManager, background);

        this.mainWindow = new MasterActionWindow(sparrowTerminal);
        addWindow(mainWindow);

        Panel panel = new Panel(new BorderLayout());

        Panel titleBar = new Panel(new GridLayout(2));
        new Label("Sparrow Terminal").addTo(titleBar);
        this.connectedLabel = new Label("Disconnected");
        titleBar.addComponent(connectedLabel, GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, true, false));
        panel.addComponent(titleBar, BorderLayout.Location.TOP);

        panel.addComponent(new EmptySpace(TextColor.ANSI.BLUE));

        Panel statusBar = new Panel(new GridLayout(2));
        this.statusLabel = new Label("").addTo(statusBar);
        this.statusProgress = new ProgressBar(0, 100, 10);
        statusBar.addComponent(statusProgress, GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER, true, false));
        statusProgress.setVisible(false);
        statusProgress.setLabelFormat(null);
        progressProperty.addListener((observable, oldValue, newValue) -> statusProgress.setValue((int) (newValue.doubleValue() * 100)));

        panel.addComponent(statusBar, BorderLayout.Location.BOTTOM);
        getBackgroundPane().setComponent(panel);

        getMainWindow().addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onResized(Window window, TerminalSize oldSize, TerminalSize newSize) {
                titleBar.invalidate();
                statusBar.invalidate();
            }
        });

        AppServices.get().start();
    }

    public BasicWindow getMainWindow() {
        return mainWindow;
    }

    private void setConnectedLabel(boolean connected) {
        getGUIThread().invokeLater(() -> {
            connectedLabel.setText(connected ? "Connected" : "Disconnected");
        });
    }

    @Subscribe
    public void connectionStart(ConnectionStartEvent event) {
        statusUpdated(new StatusEvent(event.getStatus(), 120));
    }

    @Subscribe
    public void connectionFailed(ConnectionFailedEvent event) {
        setConnectedLabel(false);
        statusUpdated(new StatusEvent("Connection failed: " + event.getMessage()));
    }

    @Subscribe
    public void connection(ConnectionEvent event) {
        setConnectedLabel(true);
        statusUpdated(new StatusEvent("Connected to " + Config.get().getServerDisplayName() + " at height " + event.getBlockHeight()));
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        if(!AppServices.isConnecting() && !AppServices.isConnected()) {
            setConnectedLabel(false);
            statusUpdated(new StatusEvent("Disconnected"));
        }
    }

    @Subscribe
    public void statusUpdated(StatusEvent event) {
        getGUIThread().invokeLater(() -> statusLabel.setText(event.getStatus()));

        if(wait != null && wait.getStatus() == Animation.Status.RUNNING) {
            wait.stop();
        }
        wait = new PauseTransition(Duration.seconds(event.getShowDuration()));
        wait.setOnFinished((e) -> {
            if(statusLabel.getText().equals(event.getStatus())) {
                getGUIThread().invokeLater(() -> statusLabel.setText(""));
            }
        });
        wait.play();
    }

    @Subscribe
    public void timedEvent(TimedEvent event) {
        if(event.getTimeMills() == 0) {
            getGUIThread().invokeLater(() -> {
                statusLabel.setText("");
                statusProgress.setVisible(false);
                statusProgress.setValue(0);
            });
        } else if(event.getTimeMills() < 0) {
            getGUIThread().invokeLater(() -> {
                statusLabel.setText(event.getStatus());
                statusProgress.setVisible(false);
            });
        } else {
            getGUIThread().invokeLater(() -> {
                statusLabel.setText(event.getStatus());
                statusProgress.setVisible(true);
            });
            statusTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(progressProperty, 0)),
                    new KeyFrame(Duration.millis(event.getTimeMills()), e -> {
                        getGUIThread().invokeLater(() -> {
                            statusLabel.setText("");
                            statusProgress.setVisible(false);
                        });
                    }, new KeyValue(progressProperty, 1))
            );
            statusTimeline.setCycleCount(1);
            statusTimeline.play();
        }
    }

    @Subscribe
    public void walletHistoryStarted(WalletHistoryStartedEvent event) {
        statusUpdated(new StatusEvent("Loading wallet history..."));
    }

    @Subscribe
    public void walletHistoryFinished(WalletHistoryFinishedEvent event) {
        if(statusLabel.getText().equals("Loading wallet history...")) {
            getGUIThread().invokeLater(() -> statusLabel.setText(""));
        }
    }

    @Subscribe
    public void walletHistoryFailed(WalletHistoryFailedEvent event) {
        walletHistoryFinished(new WalletHistoryFinishedEvent(event.getWallet()));
        statusUpdated(new StatusEvent("Error retrieving wallet history" + (Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER ? ", trying another server..." : "")));
    }
}
