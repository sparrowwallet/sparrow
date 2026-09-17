package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class IrohTransport extends TcpTransport {
    private static final Logger log = LoggerFactory.getLogger(IrohTransport.class);
    private static final String BRIDGE_BINARY = "iroh-electrum-bridge";
    private Process bridgeProcess;
    private ServerSocket serverSocket;

    public IrohTransport(HostAndPort server) {
        super(server);
    }

    @Override
    protected void createSocket() throws IOException {
        String nodeId = server.getHost();
        File bridge = findBridge();
        if(bridge == null) throw new IOException("iroh-electrum-bridge binary not found");
        serverSocket = new ServerSocket(0);
        int localPort = serverSocket.getLocalPort();
        ProcessBuilder pb = new ProcessBuilder(bridge.getAbsolutePath(), nodeId);
        pb.redirectErrorStream(false);
        bridgeProcess = pb.start();
        Thread acceptThread = new Thread(() -> {
            try {
                Socket clientSocket = serverSocket.accept();
                Thread toSocket = new Thread(() -> {
                    try { pipe(bridgeProcess.getInputStream(), clientSocket.getOutputStream()); }
                    catch(IOException e) { log.debug("Bridge stdout pipe closed"); }
                }, "iroh-to-socket");
                toSocket.setDaemon(true);
                toSocket.start();
                Thread fromSocket = new Thread(() -> {
                    try { pipe(clientSocket.getInputStream(), bridgeProcess.getOutputStream()); }
                    catch(IOException e) { log.debug("Bridge stdin pipe closed"); }
                }, "socket-to-iroh");
                fromSocket.setDaemon(true);
                fromSocket.start();
            } catch(IOException e) { log.error("Bridge accept error", e); }
        }, "iroh-bridge-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        // Wait for bridge to signal ready on stderr
        Thread readyWaiter = new Thread(() -> {
            try {
                java.io.BufferedReader err = new java.io.BufferedReader(
                    new java.io.InputStreamReader(bridgeProcess.getErrorStream()));
                String line;
                while((line = err.readLine()) != null) {
                    log.debug("Bridge: " + line);
                    if(line.equals("READY")) break;
                }
            } catch(java.io.IOException e) {
                log.warn("Bridge stderr error", e);
            }
        }, "iroh-bridge-ready");
        readyWaiter.setDaemon(true);
        readyWaiter.start();
        try { readyWaiter.join(60000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }

        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", localPort));
    }

    private void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[4096];
        int n;
        while((n = in.read(buf)) != -1) { out.write(buf, 0, n); out.flush(); }
    }

    private File findBridge() {
        File jar = new File(IrohTransport.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        File next = new File(jar.getParentFile(), BRIDGE_BINARY);
        if(next.exists() && next.canExecute()) return next;
        String path = System.getenv("PATH");
        if(path != null) {
            for(String dir : path.split(File.pathSeparator)) {
                File f = new File(dir, BRIDGE_BINARY);
                if(f.exists() && f.canExecute()) return f;
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if(bridgeProcess != null) bridgeProcess.destroy();
    }
}