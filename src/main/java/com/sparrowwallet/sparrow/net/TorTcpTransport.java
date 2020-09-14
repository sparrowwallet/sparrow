package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StatusEvent;
import com.sparrowwallet.sparrow.event.TorStatusEvent;
import javafx.application.Platform;
import org.berndpruenster.netlayer.tor.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;

public class TorTcpTransport extends TcpTransport {
    public static final String TOR_DIR_PREFIX = "tor";

    public TorTcpTransport(HostAndPort server) {
        super(server);
    }

    @Override
    protected Socket createSocket() throws IOException {
        if(Tor.getDefault() == null) {
            Platform.runLater(() -> {
                String status = "Starting Tor...";
                EventManager.get().post(new TorStatusEvent(status));
            });

            Path path = Files.createTempDirectory(TOR_DIR_PREFIX);
            File torInstallDir = path.toFile();
            torInstallDir.deleteOnExit();
            try {
                LinkedHashMap<String, String> torrcOptionsMap = new LinkedHashMap<>();
                torrcOptionsMap.put("DisableNetwork", "0");
                Torrc override = new Torrc(torrcOptionsMap);

                NativeTor nativeTor = new NativeTor(torInstallDir, Collections.emptyList(), override);
                Tor.setDefault(nativeTor);
            } catch(TorCtlException e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }

        Platform.runLater(() -> {
            String status = "Tor running, connecting to " + server.toString() + "...";
            EventManager.get().post(new TorStatusEvent(status));
        });

        return new TorSocket(server.getHost(), server.getPort(), "sparrow");
    }
}
