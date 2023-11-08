package com.sparrowwallet.sparrow.terminal;

import com.google.common.net.HostAndPort;
import com.googlecode.lanterna.gui2.Label;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

public class ProxyStatusLabel extends Label {
    private static final Logger log = LoggerFactory.getLogger(ProxyStatusLabel.class);

    private final ProxyConnectionTest proxyConnectionTest = new ProxyConnectionTest();

    public ProxyStatusLabel() {
        super("");
    }

    public void update() {
        if(!AppServices.isUsingProxy()) {
            proxyConnectionTest.cancel();
            getTextGUI().getGUIThread().invokeLater(() -> setText(""));
        } else if(!Config.get().isUseProxy()) {
            proxyConnectionTest.cancel();
            if(AppServices.isTorRunning()) {
                getTextGUI().getGUIThread().invokeLater(() -> setText("Proxy enabled"));
            }
        } else if(!proxyConnectionTest.isRunning()) {
            if(proxyConnectionTest.getState() == Worker.State.CANCELLED || proxyConnectionTest.getState() == Worker.State.FAILED) {
                proxyConnectionTest.reset();
            }
            proxyConnectionTest.setPeriod(Duration.seconds(20.0));
            proxyConnectionTest.setBackoffStrategy(null);
            proxyConnectionTest.setOnSucceeded(workerStateEvent -> {
                getTextGUI().getGUIThread().invokeLater(() -> setText("Proxy enabled"));
            });
            proxyConnectionTest.setOnFailed(workerStateEvent -> {
                getTextGUI().getGUIThread().invokeLater(() -> setText("Proxy error!"));
                log.warn("Failed to connect to external Tor proxy: " + workerStateEvent.getSource().getException().getMessage());
            });
            proxyConnectionTest.start();
        }
    }

    private static class ProxyConnectionTest extends ScheduledService<Void> {
        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                protected Void call() throws IOException {
                    HostAndPort proxyHostAndPort = HostAndPort.fromString(Config.get().getProxyServer());
                    Socket socket = new Socket(proxyHostAndPort.getHost(), proxyHostAndPort.getPort());
                    socket.close();

                    return null;
                }
            };
        }
    }
}
