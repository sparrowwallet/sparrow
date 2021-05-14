package com.sparrowwallet.sparrow.io;

import com.google.gson.*;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.Argon2KeyDeriver;
import com.sparrowwallet.drongo.crypto.AsymmetricKeyDeriver;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static com.sparrowwallet.drongo.crypto.Argon2KeyDeriver.SPRW1_PARAMETERS;

public class JsonPersistence implements Persistence {
    public static final String HEADER_MAGIC_1 = "SPRW1";
    public static final int BINARY_HEADER_LENGTH = 28;

    private final Gson gson;
    private AsymmetricKeyDeriver keyDeriver;

    public JsonPersistence() {
        this.gson = getGson();
    }

    public Wallet loadWallet(File jsonFile) throws IOException {
        try(Reader reader = new FileReader(jsonFile)) {
            return gson.fromJson(reader, Wallet.class);
        }
    }

    public WalletBackupAndKey loadWallet(File walletFile, CharSequence password) throws IOException, StorageException {
        Wallet wallet;
        ECKey encryptionKey;

        try(InputStream fileStream = new FileInputStream(walletFile)) {
            encryptionKey = getEncryptionKey(password, fileStream, null);
            Reader reader = new InputStreamReader(new InflaterInputStream(new ECIESInputStream(fileStream, encryptionKey, getEncryptionMagic())), StandardCharsets.UTF_8);
            wallet = gson.fromJson(reader, Wallet.class);
        }

        return new WalletBackupAndKey(wallet, null, encryptionKey, keyDeriver, null);
    }

    public Map<File, Wallet> loadWallets(File[] walletFiles, ECKey encryptionKey) throws IOException, StorageException {
        Map<File, Wallet> walletsMap = new LinkedHashMap<>();
        for(File file : walletFiles) {
            if(encryptionKey != null) {
                try(InputStream fileStream = new FileInputStream(file)) {
                    encryptionKey = getEncryptionKey(null, fileStream, encryptionKey);
                    Reader reader = new InputStreamReader(new InflaterInputStream(new ECIESInputStream(fileStream, encryptionKey, getEncryptionMagic())), StandardCharsets.UTF_8);
                    walletsMap.put(file, gson.fromJson(reader, Wallet.class));
                }
            } else {
                walletsMap.put(file, loadWallet(file));
            }
        }

        return walletsMap;
    }

    public Map<Storage, WalletBackupAndKey> loadChildWallets(File walletFile, Wallet masterWallet, ECKey encryptionKey) throws IOException, StorageException {
        File[] walletFiles = getChildWalletFiles(walletFile, masterWallet);
        Map<Storage, WalletBackupAndKey> childWallets = new LinkedHashMap<>();
        Map<File, Wallet> loadedWallets = loadWallets(walletFiles, encryptionKey);
        for(Map.Entry<File, Wallet> entry : loadedWallets.entrySet()) {
            Storage storage = new Storage(entry.getKey());
            storage.setEncryptionPubKey(encryptionKey == null ? Storage.NO_PASSWORD_KEY : ECKey.fromPublicOnly(encryptionKey));
            storage.setKeyDeriver(getKeyDeriver());
            Wallet childWallet = entry.getValue();
            childWallet.setMasterWallet(masterWallet);
            childWallets.put(storage, new WalletBackupAndKey(childWallet, null, encryptionKey, keyDeriver, Collections.emptyMap()));
        }

        return childWallets;
    }

    private File[] getChildWalletFiles(File walletFile, Wallet masterWallet) {
        File childDir = new File(walletFile.getParentFile(), masterWallet.getName() + "-child");
        if(!childDir.exists()) {
            return new File[0];
        }

        File[] childFiles = childDir.listFiles(pathname -> {
            FileType fileType = IOUtils.getFileType(pathname);
            return pathname.getName().startsWith(masterWallet.getName()) && (fileType == FileType.BINARY || fileType == FileType.JSON);
        });

        return childFiles == null ? new File[0] : childFiles;
    }

    public File storeWallet(File walletFile, Wallet wallet) throws IOException {
        File parent = walletFile.getParentFile();
        if(!parent.exists() && !Storage.createOwnerOnlyDirectory(parent)) {
            throw new IOException("Could not create folder " + parent);
        }

        if(!walletFile.getName().endsWith(".json")) {
            File jsonFile = new File(parent, walletFile.getName() + ".json");
            if(walletFile.exists()) {
                if(!walletFile.renameTo(jsonFile)) {
                    throw new IOException("Could not rename " + walletFile.getName() + " to " + jsonFile.getName());
                }
            }
            walletFile = jsonFile;
        }

        if(!walletFile.exists()) {
            Storage.createOwnerOnlyFile(walletFile);
        }

        try(Writer writer = new FileWriter(walletFile)) {
            gson.toJson(wallet, writer);
        }

        return walletFile;
    }

    public File storeWallet(File walletFile, Wallet wallet, ECKey encryptionPubKey) throws IOException {
        File parent = walletFile.getParentFile();
        if(!parent.exists() && !Storage.createOwnerOnlyDirectory(parent)) {
            throw new IOException("Could not create folder " + parent);
        }

        if(walletFile.getName().endsWith(".json")) {
            File noJsonFile = new File(parent, walletFile.getName().substring(0, walletFile.getName().lastIndexOf('.')));
            if(walletFile.exists()) {
                if(!walletFile.renameTo(noJsonFile)) {
                    throw new IOException("Could not rename " + walletFile.getName() + " to " + noJsonFile.getName());
                }
            }
            walletFile = noJsonFile;
        }

        if(!walletFile.exists()) {
            Storage.createOwnerOnlyFile(walletFile);
        }

        try(OutputStream outputStream = new FileOutputStream(walletFile)) {
            writeBinaryHeader(outputStream);
            OutputStreamWriter writer = new OutputStreamWriter(new DeflaterOutputStream(new ECIESOutputStream(outputStream, encryptionPubKey, getEncryptionMagic())), StandardCharsets.UTF_8);
            gson.toJson(wallet, writer);
            //Close the writer explicitly as the try-resources block will not do so
            writer.close();
        }

        return walletFile;
    }

    private void writeBinaryHeader(OutputStream outputStream) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(21);
        buf.put(HEADER_MAGIC_1.getBytes(StandardCharsets.UTF_8));
        buf.put(keyDeriver.getSalt());

        byte[] encoded = Base64.getEncoder().encode(buf.array());
        if(encoded.length != BINARY_HEADER_LENGTH) {
            throw new IllegalStateException("Header length not " + BINARY_HEADER_LENGTH + " bytes");
        }
        outputStream.write(encoded);
    }

    private static byte[] getEncryptionMagic() {
        return "BIE1".getBytes(StandardCharsets.UTF_8);
    }

    public ECKey getEncryptionKey(CharSequence password) throws IOException, StorageException {
        return getEncryptionKey(password, null, null);
    }

    private ECKey getEncryptionKey(CharSequence password, InputStream inputStream, ECKey alreadyDerivedKey) throws IOException, StorageException {
        if(password != null && password.equals("")) {
            return Storage.NO_PASSWORD_KEY;
        }

        AsymmetricKeyDeriver keyDeriver = getKeyDeriver(inputStream);
        return alreadyDerivedKey == null ?  keyDeriver.deriveECKey(password) : alreadyDerivedKey;
    }

    public AsymmetricKeyDeriver getKeyDeriver() {
        return keyDeriver;
    }

    public void setKeyDeriver(AsymmetricKeyDeriver keyDeriver) {
        this.keyDeriver = keyDeriver;
    }

    private AsymmetricKeyDeriver getKeyDeriver(InputStream inputStream) throws IOException, StorageException {
        if(keyDeriver == null) {
            keyDeriver = getWalletKeyDeriver(inputStream);
        } else if(inputStream != null) {
            inputStream.skip(BINARY_HEADER_LENGTH);
        }

        return keyDeriver;
    }

    private AsymmetricKeyDeriver getWalletKeyDeriver(InputStream inputStream) throws IOException, StorageException {
        byte[] salt = new byte[SPRW1_PARAMETERS.saltLength];

        if(inputStream != null) {
            byte[] header = new byte[BINARY_HEADER_LENGTH];
            int read = inputStream.read(header);
            if(read != BINARY_HEADER_LENGTH) {
                throw new StorageException("Not a Sparrow wallet - invalid header");
            }
            try {
                byte[] decodedHeader = Base64.getDecoder().decode(header);
                byte[] magic = Arrays.copyOfRange(decodedHeader, 0, HEADER_MAGIC_1.length());
                if(!HEADER_MAGIC_1.equals(new String(magic, StandardCharsets.UTF_8))) {
                    throw new StorageException("Not a Sparrow wallet - invalid magic");
                }
                salt = Arrays.copyOfRange(decodedHeader, HEADER_MAGIC_1.length(), decodedHeader.length);
            } catch(IllegalArgumentException e) {
                throw new StorageException("Not a Sparrow wallet - invalid header");
            }
        } else {
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(salt);
        }

        return new Argon2KeyDeriver(salt);
    }

    public PersistenceType getType() {
        return PersistenceType.JSON;
    }

    public static Gson getGson() {
        return getGson(true);
    }

    private static Gson getGson(boolean includeWalletSerializers) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(ExtendedKey.class, new ExtendedPublicKeySerializer());
        gsonBuilder.registerTypeAdapter(ExtendedKey.class, new ExtendedPublicKeyDeserializer());
        gsonBuilder.registerTypeAdapter(byte[].class, new ByteArraySerializer());
        gsonBuilder.registerTypeAdapter(byte[].class, new ByteArrayDeserializer());
        gsonBuilder.registerTypeAdapter(Sha256Hash.class, new Sha256HashSerializer());
        gsonBuilder.registerTypeAdapter(Sha256Hash.class, new Sha256HashDeserializer());
        gsonBuilder.registerTypeAdapter(Date.class, new DateSerializer());
        gsonBuilder.registerTypeAdapter(Date.class, new DateDeserializer());
        gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionSerializer());
        gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionDeserializer());
        if(includeWalletSerializers) {
            gsonBuilder.registerTypeAdapter(Keystore.class, new KeystoreSerializer());
            gsonBuilder.registerTypeAdapter(WalletNode.class, new NodeSerializer());
            gsonBuilder.registerTypeAdapter(WalletNode.class, new NodeDeserializer());
        }

        gsonBuilder.addSerializationExclusionStrategy(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                return field.getDeclaringClass() == Wallet.class && field.getName().equals("masterWallet");
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        });

        return gsonBuilder.setPrettyPrinting().disableHtmlEscaping().create();
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

    private static class Sha256HashSerializer implements JsonSerializer<Sha256Hash> {
        @Override
        public JsonElement serialize(Sha256Hash src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    private static class Sha256HashDeserializer implements JsonDeserializer<Sha256Hash> {
        @Override
        public Sha256Hash deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Sha256Hash.wrap(json.getAsJsonPrimitive().getAsString());
        }
    }

    private static class DateSerializer implements JsonSerializer<Date> {
        @Override
        public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getTime());
        }
    }

    private static class DateDeserializer implements JsonDeserializer<Date> {
        @Override
        public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new Date(json.getAsJsonPrimitive().getAsLong());
        }
    }

    private static class TransactionSerializer implements JsonSerializer<Transaction> {
        @Override
        public JsonElement serialize(Transaction src, Type typeOfSrc, JsonSerializationContext context) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                src.bitcoinSerializeToStream(baos);
                return new JsonPrimitive(Utils.bytesToHex(baos.toByteArray()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static class TransactionDeserializer implements JsonDeserializer<Transaction> {
        @Override
        public Transaction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            byte[] rawTx = Utils.hexToBytes(json.getAsJsonPrimitive().getAsString());
            return new Transaction(rawTx);
        }
    }

    private static class KeystoreSerializer implements JsonSerializer<Keystore> {
        @Override
        public JsonElement serialize(Keystore keystore, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = (JsonObject)getGson(false).toJsonTree(keystore);
            if(keystore.hasPrivateKey()) {
                jsonObject.remove("extendedPublicKey");
                jsonObject.getAsJsonObject("keyDerivation").remove("masterFingerprint");
            }

            return jsonObject;
        }
    }

    private static class NodeSerializer implements JsonSerializer<WalletNode> {
        @Override
        public JsonElement serialize(WalletNode node, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = (JsonObject)getGson(false).toJsonTree(node);

            JsonArray children = jsonObject.getAsJsonArray("children");
            Iterator<JsonElement> iter = children.iterator();
            while(iter.hasNext()) {
                JsonObject childObject = (JsonObject)iter.next();
                removeEmptyCollection(childObject, "children");
                removeEmptyCollection(childObject, "transactionOutputs");

                if(childObject.get("label") == null && childObject.get("children") == null && childObject.get("transactionOutputs") == null) {
                    iter.remove();
                }
            }

            return jsonObject;
        }

        private void removeEmptyCollection(JsonObject jsonObject, String memberName) {
            if(jsonObject.get(memberName) != null && jsonObject.getAsJsonArray(memberName).size() == 0) {
                jsonObject.remove(memberName);
            }
        }
    }

    private static class NodeDeserializer implements JsonDeserializer<WalletNode> {
        @Override
        public WalletNode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            WalletNode node = getGson(false).fromJson(json, typeOfT);
            node.parseDerivation();

            for(WalletNode childNode : node.getChildren()) {
                childNode.parseDerivation();
                if(childNode.getChildren() == null) {
                    childNode.setChildren(new TreeSet<>());
                }
                if(childNode.getTransactionOutputs() == null) {
                    childNode.setTransactionOutputs(new TreeSet<>());
                }
            }

            return node;
        }
    }
}
