package com.sparrowwallet.sparrow.whirlpool.tor;

import com.google.common.net.HostAndPort;
import com.samourai.tor.client.TorClientService;
import com.sparrowwallet.sparrow.net.TorService;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

public class SparrowTorClientService extends TorClientService {
    private static final Logger log = LoggerFactory.getLogger(SparrowTorClientService.class);

    private final Whirlpool whirlpool;

    public SparrowTorClientService(Whirlpool whirlpool) {
        this.whirlpool = whirlpool;
    }

    @Override
    public void changeIdentity() {
        HostAndPort proxy = whirlpool.getTorProxy();
        if(proxy != null) {
            Socket controlSocket = TorService.getControlSocket();
            if(controlSocket != null) {
                try {
                    writeNewNym(controlSocket);
                } catch(Exception e) {
                    log.warn("Error sending NEWNYM to " + controlSocket, e);
                }
            } else {
                HostAndPort control = HostAndPort.fromParts(proxy.getHost(), proxy.getPort() + 1);
                try(Socket socket = new Socket(control.getHost(), control.getPort())) {
                    writeNewNym(socket);
                } catch(Exception e) {
                    log.warn("Error connecting to " + control + ", no Tor ControlPort configured?");
                }
            }
        }
    }

    private void writeNewNym(Socket socket) throws IOException {
        log.debug("Sending NEWNYM to " + socket);
        socket.getOutputStream().write("AUTHENTICATE \"\"\r\n".getBytes());
        socket.getOutputStream().write("SIGNAL NEWNYM\r\n".getBytes());
    }
}
