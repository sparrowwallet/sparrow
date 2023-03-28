package com.sparrowwallet.sparrow.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Bech32;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LnurlAuth {
    private static final Logger log = LoggerFactory.getLogger(LnurlAuth.class);

    public static final ChildNumber LNURL_PURPOSE = new ChildNumber(138, true);

    private final URL url;
    private final byte[] k1;
    private final String action;

    public LnurlAuth(URI uri) throws MalformedURLException {
        String lnurl = uri.getSchemeSpecificPart();
        Bech32.Bech32Data bech32 = Bech32.decode(lnurl, 2000);
        byte[] urlBytes = Bech32.convertBits(bech32.data, 0, bech32.data.length, 5, 8, false);
        String strUrl = new String(urlBytes, StandardCharsets.UTF_8);
        this.url = new URL(strUrl);

        Map<String, String> parameterMap = new LinkedHashMap<>();
        String query = url.getQuery();
        if(query == null) {
            throw new IllegalArgumentException("No k1 parameter provided.");
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

        if(parameterMap.get("tag") == null || !parameterMap.get("tag").toLowerCase(Locale.ROOT).equals("login")) {
            throw new IllegalArgumentException("Parameter tag was not set to login.");
        }

        if(parameterMap.get("k1") == null || parameterMap.get("k1").length() != 64) {
            throw new IllegalArgumentException("Parameter k1 was absent or of incorrect length.");
        }

        try {
            this.k1 = Utils.hexToBytes(parameterMap.get("k1"));
        } catch(Exception e) {
            throw new IllegalArgumentException("Parameter k1 was not a valid hexadecimal value.");
        }

        this.action = parameterMap.get("action") == null ? "login" : parameterMap.get("action").toLowerCase(Locale.ROOT);
    }

    public String getDomain() {
        return url.getHost();
    }

    public String getLoginMessage() {
        String domain = getDomain();
        switch(action) {
            case "register":
                return "register an account on " + domain;
            case "link":
                return "link your existing account on " + domain;
            case "auth":
                return "authorise " + domain;
            case "login":
            default:
                return "login to " + domain;
        }
    }

    public void sendResponse(Wallet wallet) throws LnurlAuthException, IOException {
        URL callback = getReturnURL(wallet);

        if(log.isInfoEnabled()) {
            log.info("Sending LNURL-auth response to " + callback);
        }

        Proxy proxy = AppServices.getProxy();
        if(proxy == null && callback.getHost().toLowerCase(Locale.ROOT).endsWith(Tor.TOR_ADDRESS_SUFFIX)) {
            throw new LnurlAuthException("A Tor proxy must be configured to authenticate this resource.");
        }

        HttpURLConnection connection = proxy == null ? (HttpURLConnection) callback.openConnection() : (HttpURLConnection) callback.openConnection(proxy);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");

        StringBuilder res = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while((responseLine = br.readLine()) != null) {
                res.append(responseLine.trim());
            }
        }

        if(log.isDebugEnabled()) {
            log.debug("Received from " + callback + ": " + res);
        }

        JsonObject result = JsonParser.parseString(res.toString()).getAsJsonObject();
        String status = result.get("status").getAsString();
        if("OK".equals(status)) {
            return;
        } else if("ERROR".equals(status)) {
            String reason = result.get("reason").getAsString();
            throw new LnurlAuthException("Service returned error: " + reason);
        } else {
            throw new LnurlAuthException("Service returned unknown response: " + res);
        }
    }

    private URL getReturnURL(Wallet wallet) {
        try {
            ECKey linkingKey = deriveLinkingKey(wallet);
            byte[] signature = getSignature(linkingKey);
            return new URL(url.toString() + "&sig=" + Utils.bytesToHex(signature) + "&key=" + Utils.bytesToHex(linkingKey.getPubKey()));
        } catch(MalformedURLException e) {
            throw new IllegalStateException("Malformed return URL", e);
        }
    }

    private ECKey deriveLinkingKey(Wallet wallet) {
        if(wallet.getPolicyType() != PolicyType.SINGLE) {
            throw new IllegalArgumentException("Only singlesig wallets can authenticate.");
        }

        if(wallet.isEncrypted()) {
            throw new IllegalArgumentException("Wallet must be decrypted.");
        }

        try {
            ExtendedKey masterPrivateKey = wallet.getKeystores().get(0).getExtendedMasterPrivateKey();
            ECKey hashingKey = masterPrivateKey.getKey(List.of(LNURL_PURPOSE, ChildNumber.ZERO));
            byte[] hash = getHmacSha256Hash(hashingKey.getPrivKeyBytes(), getDomain());
            List<ChildNumber> pathIndexes = IntStream.range(0, 4).mapToLong(i -> ByteBuffer.wrap(hash, i * 4, 4).getInt() & 0xFFFFFFFFL)
                    .mapToObj(i -> new ChildNumber((int)i)).collect(Collectors.toList());

            List<ChildNumber> derivationPath = new ArrayList<>();
            derivationPath.add(LNURL_PURPOSE);
            derivationPath.addAll(pathIndexes);
            return masterPrivateKey.getKey(derivationPath);
        } catch(Exception e) {
            throw new IllegalStateException("Could not determine linking key", e);
        }
    }

    private byte[] getSignature(ECKey linkingKey) {
        ECDSASignature ecdsaSignature = linkingKey.signEcdsa(Sha256Hash.wrap(k1));
        return ecdsaSignature.encodeToDER();
    }

    private static byte[] getHmacSha256Hash(byte[] key, String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key, "HmacSHA256");
        sha256_HMAC.init(secret_key);

        return sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    public static final class LnurlAuthException extends Exception {
        public LnurlAuthException(String message) {
            super(message);
        }

        public LnurlAuthException(String message, Throwable cause) {
            super(message, cause);
        }

        public LnurlAuthException(Throwable cause) {
            super(cause);
        }

        public LnurlAuthException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
