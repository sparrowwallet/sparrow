package com.sparrowwallet.sparrow.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.tern.http.client.HttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;

public class Auth47 {
    private static final Logger log = LoggerFactory.getLogger(Auth47.class);

    public static final String SCHEME = "auth47";
    public static final String VERSION = "1.0";

    private final String nonce;
    private final URL callback;
    private final String expiry;
    private String resource;
    private boolean resourceSpecified;

    public Auth47(URI uri) throws MalformedURLException, URISyntaxException {
        this.nonce = uri.getHost();

        Map<String, String> parameterMap = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if(query == null) {
            throw new IllegalArgumentException("No callback parameter provided.");
        }

        if(query.startsWith("?")) {
            query = query.substring(1);
        }

        String[] pairs = query.split("&");
        for(String pair : pairs) {
            int idx = pair.indexOf("=");
            if(idx < 0) {
                continue;
            }
            parameterMap.put(pair.substring(0, idx), pair.substring(idx + 1));
        }

        String strCallback = parameterMap.get("c");
        if(strCallback == null) {
            throw new IllegalArgumentException("No callback parameter provided.");
        }
        URI callbackUri = new URI(strCallback);
        if(!Utils.isSecureUrl(callbackUri)) {
            throw new IllegalArgumentException("Invalid callback parameter (not https or http .onion): " + strCallback);
        }
        this.callback = callbackUri.toURL();

        this.expiry = parameterMap.get("e");
        this.resource = parameterMap.get("r");
        if(resource == null) {
            this.resource = strCallback;
        } else {
            this.resourceSpecified = !resource.equals(strCallback);
        }
    }

    public void sendResponse(Wallet wallet) throws Exception {
        if(!wallet.hasPaymentCode()) {
            throw new Auth47Exception("A software wallet is required to authenticate.");
        }

        send(getJsonResponse(wallet));
    }

    private String getJsonResponse(Wallet wallet) {
        String challenge = getChallenge();
        String signature = sign(wallet, challenge);

        Response response = new Response(VERSION, challenge, signature, wallet.getPaymentCode().toString(), null);
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        return gson.toJson(response);
    }

    private void send(String json) throws Exception {
        if(log.isInfoEnabled()) {
            log.info("Sending auth47 response " + json + " to " + callback);
        }

        HttpClientService httpClientService = AppServices.getHttpClientService();
        if(httpClientService.getTorProxy() == null && Protocol.isOnionHost(callback.getHost())) {
            throw new Auth47Exception("A Tor proxy must be configured to authenticate this resource.");
        }

        try {
            String response = httpClientService.postString(callback.toString(), null, "application/json", json);
            if(log.isDebugEnabled()) {
                log.debug("Received " + response);
            }
        } catch(HttpResponseException e) {
            if(e.getStatusCode() == 404) {
                throw new Auth47Exception("Could not authenticate. Callback URL of " + callback + " returned a 404 response.");
            }

            throw new Auth47Exception("Could not authenticate. Server returned " + e.getResponseBody());
        }
    }

    private String sign(Wallet wallet, String challenge) {
        try {
            Wallet notificationWallet = wallet.getNotificationWallet();
            WalletNode notificationNode = notificationWallet.getNode(KeyPurpose.NOTIFICATION);
            ExtendedKey extendedPrivateKey = notificationWallet.getKeystores().get(0).getBip47ExtendedPrivateKey();
            List<ChildNumber> derivation = new ArrayList<>();
            derivation.add(extendedPrivateKey.getKeyChildNumber());
            derivation.addAll(notificationNode.getDerivation());
            ECKey privKey = extendedPrivateKey.getKey(derivation);
            return privKey.signMessage(challenge, ScriptType.P2PKH);
        } catch(Exception e) {
            log.error("Error signing auth47 challenge", e);
            throw new IllegalStateException("Error signing auth47 challenge: " + e.getMessage(), e);
        }
    }

    private String getChallenge() {
        return SCHEME + "://" + nonce + "?r=" + resource + (expiry == null ? "" : "&e=" + expiry);
    }

    public URL getCallback() {
        return callback;
    }

    //The signed challenge is scoped to the resource, which need not be the callback the response is sent to
    public String getLoginMessage() {
        String callbackHost = callback.getHost();
        String resourceHost = resourceSpecified ? getResourceHost() : callbackHost;
        if(resourceHost == null) {
            return "login to an unrecognised resource (sending the response to " + callbackHost + ")";
        } else if(!resourceHost.equalsIgnoreCase(callbackHost)) {
            return "login to " + resourceHost + " (sending the response to " + callbackHost + ")";
        }

        return "login to " + callbackHost;
    }

    private String getResourceHost() {
        try {
            return new URI(resource).getHost();
        } catch(URISyntaxException e) {
            return null;
        }
    }

    private static class Response {
        public Response(String version, String challenge, String signature, String paymentCode, String address) {
            this.auth47_response = version;
            this.challenge = challenge;
            this.signature = signature;
            this.nym = paymentCode;
            this.address = address;
        }

        public String auth47_response;
        public String challenge;
        public String signature;
        public String nym;
        public String address;
    }

    public static final class Auth47Exception extends Exception {
        public Auth47Exception(String message) {
            super(message);
        }

        public Auth47Exception(String message, Throwable cause) {
            super(message, cause);
        }

        public Auth47Exception(Throwable cause) {
            super(cause);
        }

        public Auth47Exception(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
