package com.sparrowwallet.sparrow.io;

import com.google.gson.*;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public class Storage {
    public static final String SPARROW_DIR = ".sparrow";
    public static final String WALLETS_DIR = "wallets";

    private static Storage SINGLETON;

    private final Gson gson;

    private Storage() {
        gson = getGson();
    }

    public static Storage getStorage() {
        if(SINGLETON == null) {
            SINGLETON = new Storage();
        }

        return SINGLETON;
    }

    public static Gson getGson() {
        return getGson(true);
    }

    private static Gson getGson(boolean includeKeystoreSerializer) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ExtendedKey.class, new ExtendedPublicKeySerializer());
        gsonBuilder.registerTypeAdapter(ExtendedKey.class, new ExtendedPublicKeyDeserializer());
        gsonBuilder.registerTypeAdapter(byte[].class, new ByteArraySerializer());
        gsonBuilder.registerTypeAdapter(byte[].class, new ByteArrayDeserializer());
        if(includeKeystoreSerializer) {
            gsonBuilder.registerTypeAdapter(Keystore.class, new KeystoreSerializer());
        }

        return gsonBuilder.setPrettyPrinting().disableHtmlEscaping().create();
    }

    public Wallet loadWallet(File file) throws IOException {
        Reader reader = new FileReader(file);
        Wallet wallet = gson.fromJson(reader, Wallet.class);
        reader.close();

        return wallet;
    }

    public Wallet loadWallet(File file, ECKey encryptionKey) throws IOException {
        Reader reader = new InputStreamReader(new InflaterInputStream(new ECIESInputStream(new FileInputStream(file), encryptionKey, getEncryptionMagic())), StandardCharsets.UTF_8);
        Wallet wallet = gson.fromJson(reader, Wallet.class);
        reader.close();

        return wallet;
    }

    public void storeWallet(File file, Wallet wallet) throws IOException {
        File parent = file.getParentFile();
        if(!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }

        if(!file.getName().endsWith(".json")) {
            File jsonFile = new File(parent, file.getName() + ".json");
            if(file.exists()) {
                if(!file.renameTo(jsonFile)) {
                    throw new IOException("Could not rename " + file.getName() + " to " + jsonFile.getName());
                }
            }
            file = jsonFile;
        }

        Writer writer = new FileWriter(file);
        gson.toJson(wallet, writer);
        writer.close();
    }

    public void storeWallet(File file, ECKey encryptionKey, Wallet wallet) throws IOException {
        File parent = file.getParentFile();
        if(!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }

        if(file.getName().endsWith(".json")) {
            File noJsonFile = new File(parent, file.getName().substring(0, file.getName().lastIndexOf('.')));
            if(file.exists()) {
                if(!file.renameTo(noJsonFile)) {
                    throw new IOException("Could not rename " + file.getName() + " to " + noJsonFile.getName());
                }
            }
            file = noJsonFile;
        }

        OutputStreamWriter writer = new OutputStreamWriter(new DeflaterOutputStream(new ECIESOutputStream(new FileOutputStream(file), encryptionKey, getEncryptionMagic())), StandardCharsets.UTF_8);
        gson.toJson(wallet, writer);
        writer.close();
    }

    private static byte[] getEncryptionMagic() {
        return "BIE1".getBytes(StandardCharsets.UTF_8);
    }

    public File getWalletFile(String walletName) {
        return new File(getWalletsDir(), walletName);
    }

    public File getWalletsDir() {
        File walletsDir = new File(getSparrowDir(), WALLETS_DIR);
        if(!walletsDir.exists()) {
            walletsDir.mkdirs();
        }

        return walletsDir;
    }

    private File getSparrowDir() {
        return new File(getHomeDir(), SPARROW_DIR);
    }

    private File getHomeDir() {
        return new File(System.getProperty("user.home"));
    }

    private static class ExtendedPublicKeySerializer implements JsonSerializer<ExtendedKey> {
        @Override
        public JsonElement serialize(ExtendedKey src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    private static class ExtendedPublicKeyDeserializer implements JsonDeserializer<ExtendedKey> {
        @Override
        public ExtendedKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return ExtendedKey.fromDescriptor(json.getAsJsonPrimitive().getAsString());
        }
    }

    private static class ByteArraySerializer implements JsonSerializer<byte[]> {
        @Override
        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Utils.bytesToHex(src));
        }
    }

    private static class ByteArrayDeserializer implements JsonDeserializer<byte[]> {
        @Override
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Utils.hexToBytes(json.getAsJsonPrimitive().getAsString());
        }
    }

    private static class KeystoreSerializer implements JsonSerializer<Keystore> {
        @Override
        public JsonElement serialize(Keystore keystore, Type typeOfSrc, JsonSerializationContext context) {

            JsonObject jsonObject = (JsonObject)getGson(false).toJsonTree(keystore);
            if(keystore.hasSeed()) {
                jsonObject.remove("extendedPublicKey");
                jsonObject.getAsJsonObject("keyDerivation").remove("masterFingerprint");
            }

            return jsonObject;
        }
    }
}
