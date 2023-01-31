package com.sparrowwallet.sparrow.io.ckcard;

import com.google.gson.*;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.sparrow.io.CardSignFailedException;
import com.sparrowwallet.sparrow.io.CardUnluckyNumberException;
import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Util;
import org.bitcoin.Secp256k1Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.*;
import java.util.stream.Collectors;

public class CardProtocol {
    private static final Logger log = LoggerFactory.getLogger(CardProtocol.class);

    public static final byte[] OPENDIME_HEADER = "OPENDIME".getBytes(StandardCharsets.UTF_8);
    public static final List<byte[]> FACTORY_ROOT_KEYS = List.of(Utils.hexToBytes("03028a0e89e70d0ec0d932053a89ab1da7d9182bdc6d2f03e706ee99517d05d9e1"));
    public static final int MAX_PATH_DEPTH = 8;

    private final CardTransport cardTransport;
    private final Gson gson;

    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] cardPubkey;
    private byte[] lastCardNonce;

    public CardProtocol() throws CardException {
        this.cardTransport = new CardTransport();
        this.gson = new GsonBuilder().registerTypeAdapter(byte[].class, new ByteArrayToHexTypeAdapter()).create();
    }

    public CardStatus getStatus() throws CardException {
        JsonObject status = send("status");
        CardStatus cardStatus = gson.fromJson(status, CardStatus.class);
        if(cardPubkey == null) {
            cardPubkey = cardStatus.pubkey;
        }

        return cardStatus;
    }

    public CardCerts getCerts() throws CardException {
        JsonObject certs = send("certs");
        return gson.fromJson(certs, CardCerts.class);
    }

    public void verify() throws CardException {
        CardCerts cardCerts = getCerts();

        byte[] userNonce = getNonce();
        byte[] cardNonce = lastCardNonce;
        JsonObject certs = send("check", Map.of("nonce", userNonce));
        CardSignature cardSignature = gson.fromJson(certs, CardSignature.class);
        Sha256Hash verificationData = getVerificationData(cardNonce, userNonce, new byte[0]);
        ECDSASignature ecdsaSignature = cardSignature.getSignature();
        if(!ecdsaSignature.verify(verificationData.getBytes(), cardPubkey)) {
            throw new CardException("Card authentication failure: Provided signature did not match public key");
        }

        byte[] pubkey = cardPubkey;
        for(byte[] cert : cardCerts.cert_chain) {
            Sha256Hash pubkeyHash = Sha256Hash.of(pubkey);

            try {
                ECKey recoveredKey = ECKey.signedHashToKey(pubkeyHash, cert, false);
                pubkey = recoveredKey.getPubKey();
            } catch(SignatureException e) {
                throw new CardException("Card signature error", e);
            }
        }

        byte[] rootPubKey = pubkey;
        if(FACTORY_ROOT_KEYS.stream().noneMatch(key -> Arrays.equals(key, rootPubKey))) {
            throw new CardException("Card authentication failure: Could not verify to root certificate");
        }
    }

    public CardRead read(String cvc) throws CardException {
        Map<String, Object> args = new HashMap<>();
        args.put("nonce", getNonce());

        JsonObject read = sendAuth("read", args, cvc);
        return gson.fromJson(read, CardRead.class);
    }

    public CardSetup setup(String cvc, byte[] chainCode) throws CardException {
        if(chainCode == null) {
            chainCode = Sha256Hash.hashTwice(secureRandom.generateSeed(128));
        }

        if(chainCode.length != 32) {
            throw new IllegalArgumentException("Invalid chain code length of " + chainCode.length);
        }

        Map<String, Object> args = new HashMap<>();
        args.put("chain_code", chainCode);
        JsonObject setup = sendAuth("new", args, cvc);
        return gson.fromJson(setup, CardSetup.class);
    }

    public CardWait authWait() throws CardException {
        JsonObject wait = send("wait");
        return gson.fromJson(wait, CardWait.class);
    }

    public CardXpub xpub(String cvc, boolean master) throws CardException {
        Map<String, Object> args = new HashMap<>();
        args.put("master", master);

        JsonObject xpub = sendAuth("xpub", args, cvc);
        return gson.fromJson(xpub, CardXpub.class);
    }

    public CardDerive derive(String cvc, List<ChildNumber> path) throws CardException {
        if(path.stream().anyMatch(childNumber -> !childNumber.isHardened())) {
            throw new IllegalArgumentException("Derivation path cannot contain unhardened components");
        }

        if(path.size() > MAX_PATH_DEPTH) {
            throw new IllegalArgumentException("Derivation path cannot have more than " + MAX_PATH_DEPTH + " components");
        }

        if(lastCardNonce == null || cardPubkey == null) {
            getStatus();
        }

        byte[] userNonce = getNonce();
        Map<String, Object> args = new HashMap<>();
        args.put("path", path.stream().map(cn -> Integer.toUnsignedLong(cn.i())).collect(Collectors.toList()));
        args.put("nonce", userNonce);

        JsonObject derive = sendAuth("derive", args, cvc);
        return gson.fromJson(derive, CardDerive.class);
    }

    public CardSign sign(String cvc, List<ChildNumber> subpath, Sha256Hash digest) throws CardException {
        if(subpath.stream().anyMatch(ChildNumber::isHardened)) {
            throw new IllegalArgumentException("Derivation path cannot contain hardened components");
        }

        Map<String, Object> args = new HashMap<>();
        args.put("subpath", subpath.stream().map(cn -> Integer.toUnsignedLong(cn.i())).collect(Collectors.toList()));
        args.put("digest", digest.getBytes());

        for(int attempt = 0; attempt < 5; attempt++) {
            try {
                JsonObject sign = sendAuth("sign", args, cvc);
                CardSign cardSign = gson.fromJson(sign, CardSign.class);
                if(!cardSign.getSignature().verify(digest.getBytes(), cardSign.pubkey)) {
                    continue;
                }

                return cardSign;
            } catch(CardUnluckyNumberException e) {
                log.debug("Got unlucky number signing, trying again...");
            }
        }

        throw new CardSignFailedException("Failed to sign digest after 5 tries. It's safe to try again.");
    }

    public CardChange change(String currentCvc, String newCvc) throws CardException {
        if(newCvc.length() < 6 || newCvc.length() > 32) {
            throw new IllegalArgumentException("CVC cannot be of length " + newCvc.length());
        }

        Map<String, Object> args = new HashMap<>();
        args.put("data", newCvc.getBytes(StandardCharsets.UTF_8));
        JsonObject change = sendAuth("change", args, currentCvc);
        return gson.fromJson(change, CardChange.class);
    }

    public CardBackup backup(String cvc) throws CardException {
        JsonObject backup = sendAuth("backup", new HashMap<>(), cvc);
        return gson.fromJson(backup, CardBackup.class);
    }

    public CardUnseal unseal(String cvc) throws CardException {
        CardStatus status = getStatus();

        Map<String, Object> args = new HashMap<>();
        args.put("slot", status.getSlot());

        JsonObject unseal = sendAuth("unseal", args, cvc);
        return gson.fromJson(unseal, CardUnseal.class);
    }

    public void disconnect() throws CardException {
        cardTransport.disconnect();
    }

    private JsonObject send(String cmd) throws CardException {
        return send(cmd, Collections.emptyMap());
    }

    private JsonObject send(String cmd, Map<String, Object> args) throws CardException {
        JsonObject jsonObject = cardTransport.send(cmd, args);
        if(jsonObject.get("card_nonce") != null) {
            lastCardNonce = gson.fromJson(jsonObject, CardResponse.class).card_nonce;
        }

        return jsonObject;
    }

    private JsonObject sendAuth(String cmd, Map<String, Object> args, String cvc) throws CardException {
        byte[] sessionKey = addAuth(cmd, args, cvc);
        JsonObject jsonObject = send(cmd, args);

        if(jsonObject.get("privkey") != null) {
            byte[] privKeyBytes = Utils.hexToBytes(jsonObject.get("privkey").getAsString());
            jsonObject.add("privkey", new JsonPrimitive(Utils.bytesToHex(Utils.xor(sessionKey, privKeyBytes))));
        }

        return jsonObject;
    }

    public byte[] addAuth(String cmd, Map<String, Object> args, String cvc) throws CardException {
        if(cvc.length() < 6 || cvc.length() > 32) {
            throw new IllegalArgumentException("CVC cannot be of length " + cvc.length());
        }

        if(lastCardNonce == null || cardPubkey == null) {
            getStatus();
        }

        try {
            ECKey ephemeralKey = new ECKey(secureRandom);
            if(Secp256k1Context.isEnabled()) {
                byte[] sessionKey = NativeSecp256k1.createECDHSecret(ephemeralKey.getPrivKeyBytes(), cardPubkey);
                byte[] md = Sha256Hash.hash(Utils.concat(lastCardNonce, cmd.getBytes(StandardCharsets.UTF_8)));
                byte[] mask = Arrays.copyOf(Utils.xor(sessionKey, md), cvc.length());
                byte[] xcvc = Utils.xor(cvc.getBytes(StandardCharsets.UTF_8), mask);

                args.put("epubkey", ephemeralKey.getPubKey());
                args.put("xcvc", xcvc);

                if(cmd.equals("sign") && args.get("digest") instanceof byte[] digestBytes) {
                    args.put("digest", Utils.xor(digestBytes, sessionKey));
                } else if(cmd.equals("change") && args.get("data") instanceof byte[] dataBytes) {
                    args.put("data", Utils.xor(dataBytes, Arrays.copyOf(sessionKey, dataBytes.length)));
                }

                return sessionKey;
            } else {
                throw new IllegalStateException("Native library libsecp256k1 required but not enabled");
            }
        } catch(NativeSecp256k1Util.AssertFailException e) {
            throw new RuntimeException(e);
        }
    }

    private Sha256Hash getVerificationData(byte[] cardNonce, byte[] userNonce, byte[] commandData) {
        byte[] data = new byte[40 + commandData.length];
        System.arraycopy(OPENDIME_HEADER, 0, data, 0, 8);
        System.arraycopy(cardNonce, 0, data, 8, 16);
        System.arraycopy(userNonce, 0, data, 24, 16);
        System.arraycopy(commandData, 0, data, 40, commandData.length);
        return Sha256Hash.of(data);
    }

    private byte[] getNonce() {
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    private static class ByteArrayToHexTypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Utils.hexToBytes(json.getAsString());
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Utils.bytesToHex(src));
        }
    }
}
