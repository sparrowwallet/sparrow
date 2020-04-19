package com.sparrowwallet.sparrow.storage;

import com.google.gson.*;
import com.sparrowwallet.drongo.ExtendedPublicKey;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.*;
import java.lang.reflect.Type;

public class Storage {
    public static final String SPARROW_DIR = ".sparrow";
    public static final String WALLETS_DIR = "wallets";

    private static Storage SINGLETON;

    private final Gson gson;

    private Storage() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ExtendedPublicKey.class, new ExtendedPublicKeySerializer());
        gsonBuilder.registerTypeAdapter(ExtendedPublicKey.class, new ExtendedPublicKeyDeserializer());
        gson = gsonBuilder.setPrettyPrinting().create();
    }

    public static Storage getStorage() {
        if(SINGLETON == null) {
            SINGLETON = new Storage();
        }

        return SINGLETON;
    }

    public Wallet loadWallet(File file) throws IOException {
        Reader reader = new FileReader(file);
        Wallet wallet = gson.fromJson(reader, Wallet.class);
        reader.close();

        return wallet;
    }

    public void storeWallet(File file, Wallet wallet) throws IOException {
        File parent = file.getParentFile();
        if(!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }

        Writer writer = new FileWriter(file);
        gson.toJson(wallet, writer);
        writer.close();
    }

    public File getWalletFile(String walletName) {
        return new File(getWalletsDir(), walletName);
    }

    public File getWalletsDir() {
        return new File(getSparrowDir(), WALLETS_DIR);
    }

    private File getSparrowDir() {
        return new File(getHomeDir(), SPARROW_DIR);
    }

    private File getHomeDir() {
        return new File(System.getProperty("user.home"));
    }

    private static class ExtendedPublicKeySerializer implements JsonSerializer<ExtendedPublicKey> {
        @Override
        public JsonElement serialize(ExtendedPublicKey src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    private static class ExtendedPublicKeyDeserializer implements JsonDeserializer<ExtendedPublicKey> {
        @Override
        public ExtendedPublicKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return ExtendedPublicKey.fromDescriptor(json.getAsJsonPrimitive().getAsString());
        }
    }
}
