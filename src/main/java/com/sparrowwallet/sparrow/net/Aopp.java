package com.sparrowwallet.sparrow.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.sparrow.AppServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class Aopp {
    private static final Logger log = LoggerFactory.getLogger(Aopp.class);

    public static final String SCHEME = "aopp";

    private final int version;
    private final String message;
    private final ScriptType scriptType;
    private final URL callback;

    public Aopp(URI uri) throws MalformedURLException {
        if(!uri.getScheme().equals(SCHEME)) {
            throw new IllegalArgumentException("Uri " + uri + " does not have the correct scheme");
        }

        Map<String, String> parameterMap = new LinkedHashMap<>();
        String query = uri.getSchemeSpecificPart();
        if(query.startsWith("?")) {
            query = query.substring(1);
        }

        String[] pairs = query.split("&");
        for(String pair : pairs) {
            int idx = pair.indexOf("=");
            parameterMap.put(pair.substring(0, idx), pair.substring(idx + 1));
        }

        String strVersion = parameterMap.get("v");
        if(strVersion == null) {
            throw new IllegalArgumentException("No version parameter provided");
        }

        version = Integer.parseInt(strVersion);
        if(version != 0) {
            throw new IllegalArgumentException("Unsupported version number " + version);
        }

        String strMessage = parameterMap.get("msg");
        if(strMessage == null) {
            throw new IllegalArgumentException("No msg parameter provided");
        }
        message = strMessage.replace('+', ' ');

        String asset = parameterMap.get("asset");
        if(asset == null || !asset.equals("btc")) {
            throw new IllegalArgumentException("Unsupported asset type " + asset);
        }

        String format = parameterMap.get("format");
        if(format == null) {
            throw new IllegalArgumentException("No format parameter provided");
        }
        if(format.equals("p2sh")) {
            format = "p2sh_p2wpkh";
        }

        if(format.equals("any")) {
            scriptType = null;
        } else {
            scriptType = ScriptType.valueOf(format.toUpperCase());
        }

        String callbackUrl = parameterMap.get("callback");
        if(callbackUrl == null) {
            throw new IllegalArgumentException("No callback parameter provided");
        }

        callback = new URL(callbackUrl);
    }

    public void sendProofOfAddress(Address address, String signature) throws URISyntaxException, IOException, InterruptedException, AoppException {
        Proxy proxy = AppServices.getProxy();

        CallbackRequest callbackRequest = new CallbackRequest(version, address.toString(), signature);
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        String json = gson.toJson(callbackRequest);

        if(log.isDebugEnabled()) {
            log.debug("Sending " + json + " to " + callback);
        }

        HttpURLConnection connection = proxy == null ? (HttpURLConnection)callback.openConnection() : (HttpURLConnection)callback.openConnection(proxy);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try(OutputStream os = connection.getOutputStream()) {
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            os.write(jsonBytes);
        }

        StringBuilder response = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        int statusCode = connection.getResponseCode();

        if(log.isDebugEnabled()) {
            log.debug("Received " + statusCode + " " + response);
        }

        if(statusCode < 200 || statusCode >= 300) {
            throw new AoppException("Could not send proof of ownership. Server returned " + response);
        }
    }

    public ScriptType getScriptType() {
        return scriptType;
    }

    public String getMessage() {
        return message;
    }

    public URL getCallback() {
        return callback;
    }

    private static class CallbackRequest {
        public CallbackRequest(int version, String address, String signature) {
            this.version = version;
            this.address = address;
            this.signature = signature;
        }

        public int version;
        public String address;
        public String signature;
    }

    public static final class AoppException extends Exception {
        public AoppException(String message) {
            super(message);
        }

        public AoppException(String message, Throwable cause) {
            super(message, cause);
        }

        public AoppException(Throwable cause) {
            super(cause);
        }

        public AoppException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
