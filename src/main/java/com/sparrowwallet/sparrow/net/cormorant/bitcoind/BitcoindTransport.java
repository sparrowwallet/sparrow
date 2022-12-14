package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class BitcoindTransport implements Transport {
    private static final Logger log = LoggerFactory.getLogger(BitcoindTransport.class);
    public static final String COOKIE_FILENAME = ".cookie";

    private URL bitcoindUrl;
    private File cookieFile;
    private Long cookieFileTimestamp;
    private String bitcoindAuthEncoded;

    public BitcoindTransport(Server bitcoindServer, String bitcoindWallet, String bitcoindAuth) {
        this(bitcoindServer, bitcoindWallet);
        this.bitcoindAuthEncoded = Base64.getEncoder().encodeToString(bitcoindAuth.getBytes(StandardCharsets.UTF_8));
    }

    public BitcoindTransport(Server bitcoindServer, String bitcoindWallet, File bitcoindDir) {
        this(bitcoindServer, bitcoindWallet);
        this.cookieFile = new File(bitcoindDir, COOKIE_FILENAME);
    }

    private BitcoindTransport(Server bitcoindServer, String bitcoindWallet) {
        try {
            this.bitcoindUrl = new URL(bitcoindServer.getUrl() + "/wallet/" + bitcoindWallet);
        } catch(MalformedURLException e) {
            log.error("Malformed Bitcoin Core RPC URL", e);
        }
    }

    @Override
    public String pass(String request) throws IOException {
        Proxy proxy = AppServices.getProxy();
        HttpURLConnection connection = proxy == null ? (HttpURLConnection)bitcoindUrl.openConnection() : (HttpURLConnection)bitcoindUrl.openConnection(proxy);

        if(connection instanceof HttpsURLConnection httpsURLConnection) {
            SSLSocketFactory sslSocketFactory = getTrustAllSocketFactory();
            if(sslSocketFactory != null) {
                httpsURLConnection.setSSLSocketFactory(sslSocketFactory);
            }
        }

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        String auth = getBitcoindAuthEncoded();
        if(auth != null) {
            connection.setRequestProperty("Authorization", "Basic " + auth);
        }

        connection.setDoOutput(true);

        log.trace("> " + request);

        try(OutputStream os = connection.getOutputStream()) {
            byte[] jsonBytes = request.getBytes(StandardCharsets.UTF_8);
            os.write(jsonBytes);
        }

        int statusCode = connection.getResponseCode();
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
        log.trace("< " + response);

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

    private SSLSocketFactory getTrustAllSocketFactory() {
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                }
            }
        };

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            log.error("Error creating SSL socket factory", e);
        }

        return null;
    }
}
