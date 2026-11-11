package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.Protocol;
import com.sparrowwallet.sparrow.net.TcpOverTlsTransport;
import com.sparrowwallet.tern.http.client.HttpEndpointClient;
import com.sparrowwallet.tern.http.client.HttpProxy;
import com.sparrowwallet.tern.http.client.HttpProxyProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.*;

public class BitcoindTransport implements Transport, Closeable {
    private static final Logger log = LoggerFactory.getLogger(BitcoindTransport.class);
    public static final String COOKIE_FILENAME = ".cookie";

    //A node that cannot be reached must not hang the calling thread. A node is expected to be quickly reachable, but connecting to an onion address must first build a circuit.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration ONION_CONNECT_TIMEOUT = Duration.ofSeconds(60);
    //Below Bitcoin Core's default rpcservertimeout of 30 seconds, so that the node does not close a connection as it is reused
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(10);
    //A backstop against a hung thread rather than a limit on how long the user waits, since TcpTransport already bounds the Electrum requests these calls serve. Set well beyond
    //any legitimate call, and never below the timeout the Electrum layer above allows.
    private static final int READ_TIMEOUT_MILLIS = 300_000;
    //A rescan is synchronous, so importing a descriptor with an early birthday can block for hours, as can loading a wallet Bitcoin Core must first catch up to the tip
    private static final Set<String> RESCANNING_METHODS = Set.of("importdescriptors", "loadwallet");
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final Server bitcoindServer;
    //Package visible so tests need not wait it out
    int readTimeoutMillis;
    private URI bitcoindUri;
    private File cookieFile;
    private Long cookieFileTimestamp;
    private String bitcoindAuthEncoded;
    private HttpEndpointClient httpClient;
    private String httpClientProxy;
    private boolean closed;

    public BitcoindTransport(Server bitcoindServer, String bitcoindWallet, String bitcoindAuth) {
        this(bitcoindServer, bitcoindWallet);
        this.bitcoindAuthEncoded = Base64.getEncoder().encodeToString(bitcoindAuth.getBytes(StandardCharsets.UTF_8));
    }

    public BitcoindTransport(Server bitcoindServer, String bitcoindWallet, File bitcoindDir) {
        this(bitcoindServer, bitcoindWallet);
        this.cookieFile = new File(getCookieDir(bitcoindDir), COOKIE_FILENAME);
    }

    private BitcoindTransport(Server bitcoindServer, String bitcoindWallet) {
        this.bitcoindServer = bitcoindServer;
        this.readTimeoutMillis = Math.max(READ_TIMEOUT_MILLIS, Config.get().getMaxServerTimeout() * 1000);
        try {
            String serverUrl = bitcoindServer.getUrl();
            if(!bitcoindServer.getHostAndPort().hasPort()) {
                serverUrl += ":" + Network.get().getDefaultPort();
            }
            this.bitcoindUri = new URI(serverUrl + "/wallet/" + bitcoindWallet);
        } catch(URISyntaxException e) {
            log.error("Malformed Bitcoin Core RPC URL", e);
        }
    }

    @Override
    public String pass(String request) throws IOException {
        HttpProxy proxy = getHttpProxy();
        HttpEndpointClient client = getHttpClient(proxy);

        Map<String, String> headers = new HashMap<>();
        String auth = getBitcoindAuthEncoded();
        if(auth != null) {
            headers.put("Authorization", "Basic " + auth);
        }

        Duration readTimeout = isRescanningMethod(request) ? Duration.ZERO : Duration.ofMillis(readTimeoutMillis);

        log.debug("> " + request);

        //From v32.1 Bitcoin Core closes the connection from a client outside rpcallowip without answering, where earlier versions answered 403 below
        String closedMessage = "Bitcoin Core at " + bitcoindUri.getAuthority() + " closed the connection without responding, check its rpcallowip settings";

        HttpEndpointClient.Response httpResponse;
        try {
            httpResponse = client.send("POST", bitcoindUri, headers, "application/json", request.getBytes(StandardCharsets.UTF_8), readTimeout);
        } catch(EOFException e) {
            //Also thrown where the node resets the connection instead of closing it, as the client reports a failed read or write on a connected socket as its end
            throw new IOException(closedMessage, e);
        }

        int statusCode = httpResponse.statusCode();

        //Trust on first use, as for non-CA-certified Electrum servers: the certificate presented by the node on the first connection is saved, and required on all connections thereafter
        if(isHttps() && Storage.getCertificateFile(bitcoindServer.getHost()) == null) {
            Certificate[] certs = httpResponse.serverCertificates();
            if(certs.length > 0) {
                Storage.saveCertificate(bitcoindServer.getHost(), certs[0]);
                closeHttpClient();
            }
        }

        if(statusCode == 401) {
            throw new IOException((cookieFile == null ? "User/pass" : "Cookie file") + " authentication failed");
        } else if(statusCode == 403) {
            throw new IOException("Bitcoin Core at " + bitcoindUri.getAuthority() + " refused RPC access from this computer, check its rpcallowip and rpcwhitelist settings");
        }

        StringBuilder res = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(httpResponse.body()), StandardCharsets.UTF_8))) {
            String responseLine;
            while((responseLine = br.readLine()) != null) {
                //Bitcoin Core returns a null result alongside the error on a failed call, which the client deserializes to a null return value instead of throwing the error.
                //It answers 400 on an invalid request and 404 on an unknown method, so stripping the null result on 500 alone loses those errors and returns null to the caller.
                if(statusCode >= 400) {
                    responseLine = responseLine.replace("\"result\":null,", "");
                }

                res.append(responseLine.trim());
            }
        }

        String response = res.toString();
        log.debug("< " + response);

        //A response carrying neither a result nor an error leaves the client constructing a JsonRpcException from a null error message, which throws a NullPointerException naming nothing.
        //Bitcoin Core always answers with a JSON-RPC object, so an empty or HTML body here comes from something else on the network path - typically a TLS terminating proxy sent a plain HTTP request.
        if(!response.startsWith("{")) {
            throw new IOException("Bitcoin Core at " + bitcoindUri.getAuthority() + " did not return a JSON-RPC response to the " + bitcoindUri.getScheme() + " request (HTTP " + statusCode + ")");
        }

        return response;
    }

    //Bitcoin Core RPC is a connection to the user's own node, expected to be on this computer or the local network, or reached over its onion service or a VPN or SSH tunnel.
    //A configured proxy is therefore applied to onion addresses only - AppServices.getProxy() is also non-null whenever the internal Tor is running, and Tor can reach neither
    //loopback nor private addresses, while routing a clearnet node through it would expose the RPC credentials to an exit node.
    //Configuring a node that is neither local nor onion warns the user on testing the connection or closing the dialog, see ServerSettingsController.
    private HttpProxy getHttpProxy() {
        Proxy proxy = AppServices.getProxy();
        if(proxy == null || !Protocol.isOnionAddress(bitcoindServer) || !(proxy.address() instanceof InetSocketAddress proxyAddress)) {
            return null;
        }

        return new HttpProxy(HttpProxyProtocol.SOCKS5, proxyAddress.getHostString(), proxyAddress.getPort());
    }

    //The client keeps connections open between calls, and is replaced when the proxy changes or a certificate is saved on first use
    private synchronized HttpEndpointClient getHttpClient(HttpProxy proxy) throws IOException {
        if(closed) {
            throw new IOException("Bitcoin Core connection to " + bitcoindUri.getAuthority() + " is closed");
        }

        String proxyKey = String.valueOf(proxy);
        if(httpClient != null && !proxyKey.equals(httpClientProxy)) {
            closeHttpClient();
        }

        if(httpClient == null) {
            httpClient = new HttpEndpointClient("BitcoindTransport", proxy, isHttps() ? createSSLContext() : null, proxy == null ? CONNECT_TIMEOUT : ONION_CONNECT_TIMEOUT, IDLE_TIMEOUT);
            httpClientProxy = proxyKey;
        }

        return httpClient;
    }

    private synchronized void closeHttpClient() {
        if(httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        closeHttpClient();
    }

    private boolean isHttps() {
        return "https".equalsIgnoreCase(bitcoindUri.getScheme());
    }

    //Parses the method as TcpTransport does for Electrum notifications, so that a parameter value cannot match
    static boolean isRescanningMethod(String request) {
        try(JsonParser parser = JSON_FACTORY.createParser(request)) {
            if(parser.nextToken() != JsonToken.START_OBJECT) {
                return false;
            }
            while(parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if("method".equals(field)) {
                    return value == JsonToken.VALUE_STRING && RESCANNING_METHODS.contains(parser.getText());
                }
                parser.skipChildren();
            }
            return false;
        } catch(Exception e) {
            log.warn("Could not parse JSON-RPC request method, applying the default read timeout: " + e.getMessage());
            return false;
        }
    }

    private String getBitcoindAuthEncoded() throws IOException {
        if(cookieFile != null) {
            if(!cookieFile.exists()) {
                throw new IOException("Cannot find Bitcoin Core cookie file at " + cookieFile.getAbsolutePath());
            }

            if(cookieFileTimestamp == null || cookieFile.lastModified() != cookieFileTimestamp) {
                try {
                    String userPass = Files.readAllLines(cookieFile.toPath()).get(0);
                    bitcoindAuthEncoded = Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));
                    cookieFileTimestamp = cookieFile.lastModified();
                } catch(Exception e) {
                    log.warn("Cannot read Bitcoin Core .cookie file", e);
                }
            }
        }

        return bitcoindAuthEncoded;
    }

    private static File getCookieDir(File bitcoindDir) {
        if(Network.get() == Network.TESTNET && Files.exists(Path.of(bitcoindDir.getAbsolutePath(), "testnet3", COOKIE_FILENAME))) {
            return new File(bitcoindDir, "testnet3");
        } else if(Network.get() == Network.TESTNET4 && Files.exists(Path.of(bitcoindDir.getAbsolutePath(), "testnet4", COOKIE_FILENAME))) {
            return new File(bitcoindDir, "testnet4");
        } else if(Network.get() == Network.REGTEST && Files.exists(Path.of(bitcoindDir.getAbsolutePath(), "regtest", COOKIE_FILENAME))) {
            return new File(bitcoindDir, "regtest");
        } else if(Network.get() == Network.SIGNET && Files.exists(Path.of(bitcoindDir.getAbsolutePath(), "signet", COOKIE_FILENAME))) {
            return new File(bitcoindDir, "signet");
        }

        return bitcoindDir;
    }

    //A private node's RPC certificate is necessarily self-signed, so the certificate pinned on first use authenticates it - there is no hostname to verify
    private SSLContext createSSLContext() {
        try {
            String host = bitcoindServer.getHost();
            TrustManager[] trustManagers = TcpOverTlsTransport.getTrustManagers(Storage.getCertificateFile(host), host);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
            return sslContext;
        } catch(Exception e) {
            log.error("Error creating SSL context", e);
            return null;
        }
    }
}
