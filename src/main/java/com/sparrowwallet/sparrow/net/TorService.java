package com.sparrowwallet.sparrow.net;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import net.freehaven.tor.control.TorControlError;
import org.berndpruenster.netlayer.tor.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
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
                        torrcOptionsMap.put("HashedControlPassword", "16:D780432418F09B06609940000924317D3B9DF522A3191F8F4E597E9329");
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

    public static Socket getControlSocket() {
        Tor tor = Tor.getDefault();
        if(tor != null) {
            try {
                Class<?> torClass = Class.forName("org.berndpruenster.netlayer.tor.Tor");
                Field torControllerField = torClass.getDeclaredField("torController");
                torControllerField.setAccessible(true);
                TorController torController = (TorController)torControllerField.get(tor);

                Class<?> torControllerClass = Class.forName("org.berndpruenster.netlayer.tor.TorController");
                Field socketField = torControllerClass.getDeclaredField("socket");
                socketField.setAccessible(true);
                return (Socket)socketField.get(torController);
            } catch(Exception e) {
                log.error("Error retrieving Tor control socket", e);
            }
        }

        return null;
    }
}
