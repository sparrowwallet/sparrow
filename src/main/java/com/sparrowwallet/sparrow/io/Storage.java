package com.sparrowwallet.sparrow.io;

import com.google.common.io.ByteStreams;
import com.google.gson.*;
import com.sparrowwallet.drongo.ExtendedPublicKey;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

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

    public Gson getGson() {
        return gson;
    }

    public Wallet loadWallet(File file) throws IOException {
        Reader reader = new FileReader(file);
        Wallet wallet = gson.fromJson(reader, Wallet.class);
        reader.close();

        return wallet;
    }

    public Wallet loadWallet(File file, ECKey encryptionKey) throws IOException {
        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
        byte[] encrypted = ByteStreams.toByteArray(inputStream);
        byte[] decrypted = encryptionKey.decryptEcies(encrypted, getEncryptionMagic());
        String jsonWallet = inflate(decrypted);

        return gson.fromJson(jsonWallet, Wallet.class);
    }

    private static String inflate(byte[] encryptedWallet) {
        Inflater inflater = new Inflater();
        inflater.setInput(encryptedWallet);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[1024];
            while(!inflater.finished()) {
                int byteCount = inflater.inflate(buf);
                baos.write(buf, 0, byteCount);
            }
            inflater.end();
        } catch(DataFormatException e) {
            throw new RuntimeException(e);
        }

        return baos.toString(StandardCharsets.UTF_8);
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

    public void storeWallet(File file, ECKey encryptionKey, Wallet wallet) throws IOException {
        File parent = file.getParentFile();
        if(!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create folder " + parent);
        }

        String jsonWallet = gson.toJson(wallet);
        byte[] compressedWallet = deflate(jsonWallet);
        byte[] encryptedWallet = encryptionKey.encryptEcies(compressedWallet, getEncryptionMagic());

        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
        outputStream.write(encryptedWallet);
        outputStream.close();
    }

    private static byte[] deflate(String jsonWallet) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(jsonWallet.getBytes(StandardCharsets.UTF_8));
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while(!deflater.finished()) {
            int byteCount = deflater.deflate(buf);
            baos.write(buf, 0, byteCount);
        }
        deflater.end();

        return baos.toByteArray();
    }

    private static byte[] getEncryptionMagic() {
        return "BIE1".getBytes(StandardCharsets.UTF_8);
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
