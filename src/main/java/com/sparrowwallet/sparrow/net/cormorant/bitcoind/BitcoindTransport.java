package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.Protocol;
import com.sparrowwallet.sparrow.net.TcpOverTlsTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Base64;

public class BitcoindTransport implements Transport {
    private static final Logger log = LoggerFactory.getLogger(BitcoindTransport.class);
    public static final String COOKIE_FILENAME = ".cookie";

    private final Server bitcoindServer;
    private URL bitcoindUrl;
    private File cookieFile;
    private Long cookieFileTimestamp;
    private String bitcoindAuthEncoded;
    private SSLSocketFactory sslSocketFactory;

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
        try {
            String serverUrl = bitcoindServer.getUrl();
            if(!bitcoindServer.getHostAndPort().hasPort()) {
                serverUrl += ":" + Network.get().getDefaultPort();
            }
            this.bitcoindUrl = new URI(serverUrl + "/wallet/" + bitcoindWallet).toURL();
        } catch(MalformedURLException | URISyntaxException e) {
            log.error("Malformed Bitcoin Core RPC URL", e);
        }
    }

    @Override
    public String pass(String request) throws IOException {
        //Bitcoin Core RPC is a connection to the user's own node, expected to be on this computer or the local network, or reached over its onion service or a VPN or SSH tunnel.
        //A configured proxy is therefore applied to onion addresses only - AppServices.getProxy() is also non-null whenever the internal Tor is running, and Tor can reach neither
        //loopback nor private addresses, while routing a clearnet node through it would expose the RPC credentials below to an exit node.
        //Configuring a node that is neither local nor onion warns the user on testing the connection or closing the dialog, see ServerSettingsController.
        Proxy proxy = AppServices.getProxy();
        HttpURLConnection connection = proxy != null && Protocol.isOnionAddress(bitcoindServer) ? (HttpURLConnection)bitcoindUrl.openConnection(proxy) : (HttpURLConnection)bitcoindUrl.openConnection();

        if(connection instanceof HttpsURLConnection httpsURLConnection) {
            SSLSocketFactory sslSocketFactory = getSSLSocketFactory();
            if(sslSocketFactory != null) {
                httpsURLConnection.setSSLSocketFactory(sslSocketFactory);
                //A private node's RPC certificate is necessarily self-signed, so the certificate pinned on first use below authenticates it - there is no hostname to verify
                httpsURLConnection.setHostnameVerifier((_, _) -> true);
            }
        }

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        String auth = getBitcoindAuthEncoded();
        if(auth != null) {
            connection.setRequestProperty("Authorization", "Basic " + auth);
        }

        connection.setDoOutput(true);

        log.debug("> " + request);

        try(OutputStream os = connection.getOutputStream()) {
            byte[] jsonBytes = request.getBytes(StandardCharsets.UTF_8);
            os.write(jsonBytes);
        }

        int statusCode = connection.getResponseCode();

        //Trust on first use, as for non-CA-certified Electrum servers: the certificate presented by the node on the first connection is saved, and required on all connections thereafter
        if(connection instanceof HttpsURLConnection httpsConn && Storage.getCertificateFile(bitcoindServer.getHost()) == null) {
            try {
                Certificate[] certs = httpsConn.getServerCertificates();
                if(certs.length > 0) {
                    Storage.saveCertificate(bitcoindServer.getHost(), certs[0]);
                    sslSocketFactory = null;
                }
            } catch(SSLPeerUnverifiedException e) {
                log.warn("Could not retrieve certificate for saving", e);
            }
        }

        if(statusCode == 401) {
            throw new IOException((cookieFile == null ? "User/pass" : "Cookie file") + " authentication failed");
        }
        InputStream inputStream = connection.getErrorStream() == null ? connection.getInputStream() : connection.getErrorStream();

        StringBuilder res = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String responseLine;
            while((responseLine = br.readLine()) != null) {
                if(statusCode == 500) {
                    responseLine = responseLine.replace("\"result\":null,", "");
                }

                res.append(responseLine.trim());
            }
        }

        String response = res.toString();
        log.debug("< " + response);

        return response;
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

    private SSLSocketFactory getSSLSocketFactory() {
        if(sslSocketFactory == null) {
            sslSocketFactory = createSSLSocketFactory();
        }

        return sslSocketFactory;
    }

    private SSLSocketFactory createSSLSocketFactory() {
        try {
            String host = bitcoindServer.getHost();
            TrustManager[] trustManagers = TcpOverTlsTransport.getTrustManagers(Storage.getCertificateFile(host), host);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
            return sslContext.getSocketFactory();
        } catch(Exception e) {
            log.error("Error creating SSL socket factory", e);
            return null;
        }
    }
}
