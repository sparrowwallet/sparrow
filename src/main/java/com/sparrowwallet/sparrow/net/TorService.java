package com.sparrowwallet.sparrow.net;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import net.freehaven.tor.control.TorControlError;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.berndpruenster.netlayer.tor.Torrc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Service to start internal Tor (including a Tor proxy running on localhost:9050)
 *
 * This is a ScheduledService to take advantage of the retry on failure behaviour
 */
public class TorService extends ScheduledService<NativeTor> {
    private static final Logger log = LoggerFactory.getLogger(TorService.class);

    public static final int PROXY_PORT = 9050;
    public static final String TOR_DIR_PREFIX = "tor";
    public static final String TOR_ADDRESS_SUFFIX = ".onion";

    @Override
    protected Task<NativeTor> createTask() {
        return new Task<>() {
            protected NativeTor call() throws IOException, TorServerException {
                if(Tor.getDefault() == null) {
                    Path path = Files.createTempDirectory(TOR_DIR_PREFIX);
                    File torInstallDir = path.toFile();
                    torInstallDir.deleteOnExit();
                    try {
                        LinkedHashMap<String, String> torrcOptionsMap = new LinkedHashMap<>();
                        torrcOptionsMap.put("SocksPort", Integer.toString(PROXY_PORT));
                        torrcOptionsMap.put("DisableNetwork", "0");
                        Torrc override = new Torrc(torrcOptionsMap);

                        return new NativeTor(torInstallDir, Collections.emptyList(), override);
                    } catch(TorCtlException e) {
                        if(e.getCause() instanceof TorControlError) {
                            if(e.getCause().getMessage().contains("Failed to bind")) {
                                throw new TorServerAlreadyBoundException("Tor server already bound", e.getCause());
                            }
                            log.error("Failed to start Tor", e);
                            throw new TorServerException("Failed to start Tor", e.getCause());
                        } else {
                            log.error("Failed to start Tor", e);
                            throw new TorServerException("Failed to start Tor", e);
                        }
                    }
                }

                return null;
            }
        };
    }
}
