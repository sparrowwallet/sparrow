package com.sparrowwallet.sparrow.control;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

public class TorStatusLabel extends Label {
    private static final Logger log = LoggerFactory.getLogger(TorStatusLabel.class);

    private final TorConnectionTest torConnectionTest = new TorConnectionTest();

    public TorStatusLabel() {
        getStyleClass().add("tor-status");
        setPadding(Platform.getCurrent() == Platform.WINDOWS ? new Insets(0, 0, 1, 3) : new Insets(1, 0, 0, 3));
        setGraphic(getIcon());
        update();
    }

    public void update() {
        if(!Config.get().isUseProxy()) {
            torConnectionTest.cancel();
            if(AppServices.isTorRunning()) {
                setTooltip(new Tooltip("Internal Tor proxy enabled"));
            }
        } else if(!torConnectionTest.isRunning()) {
            if(torConnectionTest.getState() == Worker.State.CANCELLED || torConnectionTest.getState() == Worker.State.FAILED) {
                torConnectionTest.reset();
            }
            torConnectionTest.setPeriod(Duration.seconds(20.0));
            torConnectionTest.setBackoffStrategy(null);
            torConnectionTest.setOnSucceeded(workerStateEvent -> {
                getStyleClass().remove("failure");
                setTooltip(new Tooltip("External Tor proxy enabled"));
            });
            torConnectionTest.setOnFailed(workerStateEvent -> {
                if(!getStyleClass().contains("failure")) {
                    getStyleClass().add("failure");
                }
                setTooltip(new Tooltip("External Tor proxy error: " + workerStateEvent.getSource().getException().getMessage()));
                log.warn("Failed to connect to external Tor proxy: " + workerStateEvent.getSource().getException().getMessage());
            });
            torConnectionTest.start();
        }
    }

    private Node getIcon() {
        Glyph adjust = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.ADJUST);
        adjust.setFontSize(Platform.getCurrent() == Platform.WINDOWS ? 14 : 15);
        adjust.setRotate(180);

        Glyph bullseye = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.BULLSEYE);
        bullseye.setFontSize(14);

        Group group = new Group();
        group.getChildren().addAll(adjust, bullseye);

        return group;
    }

    private static class TorConnectionTest extends ScheduledService<Void> {
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
