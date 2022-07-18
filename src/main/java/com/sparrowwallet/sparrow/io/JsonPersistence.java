package com.sparrowwallet.sparrow.io;

import com.google.gson.*;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
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
import java.util.stream.Collectors;
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

    @Override
    public WalletAndKey loadWallet(Storage storage) throws IOException, StorageException {
        Wallet wallet;

        try(Reader reader = new FileReader(storage.getWalletFile())) {
            wallet = gson.fromJson(reader, Wallet.class);
            wallet.getPurposeNodes().forEach(purposeNode -> purposeNode.setWallet(wallet));
        }

        Map<WalletAndKey, Storage> childWallets = loadChildWallets(storage, wallet, null);
        wallet.setChildWallets(childWallets.keySet().stream().map(WalletAndKey::getWallet).collect(Collectors.toList()));

        return new WalletAndKey(wallet, null, null, childWallets);
    }

    @Override
    public WalletAndKey loadWallet(Storage storage, CharSequence password) throws IOException, StorageException {
        return loadWallet(storage, password, null);
    }

    @Override
    public WalletAndKey loadWallet(Storage storage, CharSequence password, ECKey alreadyDerivedKey) throws IOException, StorageException {
        Wallet wallet;
        ECKey encryptionKey;

        try(InputStream fileStream = new FileInputStream(storage.getWalletFile())) {
            encryptionKey = getEncryptionKey(password, fileStream, alreadyDerivedKey);
            Reader reader = new InputStreamReader(new InflaterInputStream(new ECIESInputStream(fileStream, encryptionKey, getEncryptionMagic())), StandardCharsets.UTF_8);
            wallet = gson.fromJson(reader, Wallet.class);
            wallet.getPurposeNodes().forEach(purposeNode -> purposeNode.setWallet(wallet));
        }

        Map<WalletAndKey, Storage> childWallets = loadChildWallets(storage, wallet, encryptionKey);
        wallet.setChildWallets(childWallets.keySet().stream().map(WalletAndKey::getWallet).collect(Collectors.toList()));

        return new WalletAndKey(wallet, encryptionKey, keyDeriver, childWallets);
    }

    private Map<WalletAndKey, Storage> loadChildWallets(Storage storage, Wallet masterWallet, ECKey encryptionKey) throws IOException, StorageException {
        File[] walletFiles = getChildWalletFiles(storage.getWalletFile(), masterWallet);
        Map<WalletAndKey, Storage> childWallets = new TreeMap<>();
        for(File childFile : walletFiles) {
            Wallet childWallet = loadWallet(childFile, encryptionKey);
            childWallet.getPurposeNodes().forEach(purposeNode -> purposeNode.setWallet(childWallet));
            Storage childStorage = new Storage(childFile);
            childStorage.setEncryptionPubKey(encryptionKey == null ? Storage.NO_PASSWORD_KEY : ECKey.fromPublicOnly(encryptionKey));
            childStorage.setKeyDeriver(getKeyDeriver());
            childWallet.setMasterWallet(masterWallet);
            childWallets.put(new WalletAndKey(childWallet, encryptionKey, keyDeriver, Collections.emptyMap()), storage);
        }

        return childWallets;
    }

    private Wallet loadWallet(File walletFile, ECKey encryptionKey) throws IOException, StorageException {
        if(encryptionKey != null) {
            try(InputStream fileStream = new FileInputStream(walletFile)) {
                encryptionKey = getEncryptionKey(null, fileStream, encryptionKey);
                Reader reader = new InputStreamReader(new InflaterInputStream(new ECIESInputStream(fileStream, encryptionKey, getEncryptionMagic())), StandardCharsets.UTF_8);
                return gson.fromJson(reader, Wallet.class);
            }
        } else {
            try(Reader reader = new FileReader(walletFile)) {
                return gson.fromJson(reader, Wallet.class);
            }
        }
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

    @Override
    public File storeWallet(Storage storage, Wallet wallet) throws IOException {
        File walletFile = storage.getWalletFile();

        if(!walletFile.getName().endsWith(".json")) {
            File jsonFile = new File(walletFile.getParentFile(), walletFile.getName() + ".json");
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

    @Override
    public File storeWallet(Storage storage, Wallet wallet, ECKey encryptionPubKey) throws IOException {
        File walletFile = storage.getWalletFile();

        if(walletFile.getName().endsWith(".json")) {
            File noJsonFile = new File(walletFile.getParentFile(), walletFile.getName().substring(0, walletFile.getName().lastIndexOf('.')));
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

    @Override
    public void updateWallet(Storage storage, Wallet wallet) throws IOException {
        storeWallet(storage, wallet);
    }

    @Override
    public void updateWallet(Storage storage, Wallet wallet, ECKey encryptionPubKey) throws IOException {
        storeWallet(storage, wallet, encryptionPubKey);
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

    @Override
    public boolean isPersisted(Storage storage, Wallet wallet) {
        return storage.getWalletFile().exists();
    }

    @Override
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

    @Override
    public AsymmetricKeyDeriver getKeyDeriver() {
        return keyDeriver;
    }

    @Override
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

    @Override
    public PersistenceType getType() {
        return PersistenceType.JSON;
    }

    @Override
    public boolean isEncrypted(File walletFile) throws IOException {
        FileType fileType = IOUtils.getFileType(walletFile);
        if(FileType.JSON.equals(fileType)) {
            return false;
        } else if(FileType.BINARY.equals(fileType)) {
            try(FileInputStream fileInputStream = new FileInputStream(walletFile)) {
                getWalletKeyDeriver(fileInputStream);
                return true;
            } catch(StorageException e) {
                return false;
            }
        }

        throw new IOException("Unsupported file type");
    }

    @Override
    public String getWalletId(Storage storage, Wallet wallet) {
        return storage.getWalletFile().getParentFile().getAbsolutePath() + File.separator + getWalletName(storage.getWalletFile(), null) + ":" + (wallet == null || wallet.isMasterWallet() ? "master" : wallet.getName());
    }

    @Override
    public String getWalletName(File walletFile, Wallet wallet) {
        String name = walletFile.getName();
        if(name.endsWith("." + getType().getExtension())) {
            name = name.substring(0, name.lastIndexOf('.'));
        }

        return name;
    }

    @Override
    public void copyWallet(File walletFile, OutputStream outputStream) throws IOException {
        com.google.common.io.Files.copy(walletFile, outputStream);
    }

    @Override
    public boolean isClosed() {
        return true;
    }

    @Override
    public void close() {
        //Nothing required
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
        gsonBuilder.registerTypeAdapter(Address.class, new AddressSerializer());
        gsonBuilder.registerTypeAdapter(Address.class, new AddressDeserializer());
        if(includeWalletSerializers) {
            gsonBuilder.registerTypeAdapter(Keystore.class, new KeystoreSerializer());
            gsonBuilder.registerTypeAdapter(WalletNode.class, new NodeSerializer());
            gsonBuilder.registerTypeAdapter(WalletNode.class, new NodeDeserializer());
        }

        gsonBuilder.addSerializationExclusionStrategy(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                return field.getName().equals("id") || field.getName().equals("masterWallet") || field.getName().equals("childWallets");
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

    private static class AddressSerializer implements JsonSerializer<Address> {
        @Override
        public JsonElement serialize(Address src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    private static class AddressDeserializer implements JsonDeserializer<Address> {
        @Override
        public Address deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return Address.fromString(json.getAsJsonPrimitive().getAsString());
            } catch(InvalidAddressException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static class KeystoreSerializer implements JsonSerializer<Keystore> {
        @Override
        public JsonElement serialize(Keystore keystore, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = (JsonObject)getGson(false).toJsonTree(keystore);
            if(keystore.hasMasterPrivateKey()) {
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
