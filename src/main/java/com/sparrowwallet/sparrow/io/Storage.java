package com.sparrowwallet.sparrow.io;

import com.google.common.io.Files;
import com.google.gson.*;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.MainApp;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

import static com.sparrowwallet.drongo.crypto.Argon2KeyDeriver.SPRW1_PARAMETERS;

public class Storage {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);
    public static final ECKey NO_PASSWORD_KEY = ECKey.fromPublicOnly(ECKey.fromPrivate(Utils.hexToBytes("885e5a09708a167ea356a252387aa7c4893d138d632e296df8fbf5c12798bd28")));

    private static final DateFormat BACKUP_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final Pattern DATE_PATTERN = Pattern.compile(".+-([0-9]{14}?).*");

    public static final String SPARROW_DIR = ".sparrow";
    public static final String WINDOWS_SPARROW_DIR = "Sparrow";
    public static final String WALLETS_DIR = "wallets";
    public static final String WALLETS_BACKUP_DIR = "backup";
    public static final String CERTS_DIR = "certs";
    public static final String HEADER_MAGIC_1 = "SPRW1";
    private static final int BINARY_HEADER_LENGTH = 28;
    public static final String TEMP_BACKUP_EXTENSION = "tmp";

    private File walletFile;
    private final Gson gson;
    private AsymmetricKeyDeriver keyDeriver;
    private ECKey encryptionPubKey;

    public Storage(File walletFile) {
        this.walletFile = walletFile;
        this.gson = getGson();
    }

    public File getWalletFile() {
        return walletFile;
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

        return gsonBuilder.setPrettyPrinting().disableHtmlEscaping().create();
    }

    public WalletBackupAndKey loadWallet() throws IOException {
        Wallet wallet = loadWallet(walletFile);

        Wallet backupWallet = null;
        File[] backups = getBackups("json." + TEMP_BACKUP_EXTENSION);
        if(backups.length > 0) {
            try {
                backupWallet = loadWallet(backups[0]);
            } catch(Exception e) {
                log.error("Error loading backup wallet " + TEMP_BACKUP_EXTENSION, e);
            }
        }

        encryptionPubKey = NO_PASSWORD_KEY;
        return new WalletBackupAndKey(wallet, backupWallet, null);
    }

    public Wallet loadWallet(File jsonFile) throws IOException {
        Reader reader = new FileReader(jsonFile);
        Wallet wallet = gson.fromJson(reader, Wallet.class);
        reader.close();

        return wallet;
    }

    public WalletBackupAndKey loadWallet(CharSequence password) throws IOException, StorageException {
        WalletAndKey walletAndKey = loadWallet(walletFile, password);

        WalletAndKey backupAndKey = new WalletAndKey(null, null);
        File[] backups = getBackups(TEMP_BACKUP_EXTENSION, "json." + TEMP_BACKUP_EXTENSION);
        if(backups.length > 0) {
            try {
                backupAndKey = loadWallet(backups[0], password);
            } catch(Exception e) {
                log.error("Error loading backup wallet " + TEMP_BACKUP_EXTENSION, e);
            }
        }

        return new WalletBackupAndKey(walletAndKey.wallet, backupAndKey.wallet, walletAndKey.key);
    }

    public WalletAndKey loadWallet(File encryptedFile, CharSequence password) throws IOException, StorageException {
        InputStream fileStream = new FileInputStream(encryptedFile);
        ECKey encryptionKey = getEncryptionKey(password, fileStream);

        InputStream inputStream = new InflaterInputStream(new ECIESInputStream(fileStream, encryptionKey, getEncryptionMagic()));
        Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        Wallet wallet = gson.fromJson(reader, Wallet.class);
        reader.close();

        Key key = new Key(encryptionKey.getPrivKeyBytes(), keyDeriver.getSalt(), EncryptionType.Deriver.ARGON2);

        encryptionPubKey = ECKey.fromPublicOnly(encryptionKey);
        return new WalletAndKey(wallet, key);
    }

    public void storeWallet(Wallet wallet) throws IOException {
        if(encryptionPubKey != null && !NO_PASSWORD_KEY.equals(encryptionPubKey)) {
            storeWallet(encryptionPubKey, wallet);
            return;
        }

        File parent = walletFile.getParentFile();
        if(!parent.exists() && !parent.mkdirs()) {
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

        Writer writer = new FileWriter(walletFile);
        gson.toJson(wallet, writer);
        writer.close();
    }

    private void storeWallet(ECKey encryptionPubKey, Wallet wallet) throws IOException {
        File parent = walletFile.getParentFile();
        if(!parent.exists() && !parent.mkdirs()) {
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

        OutputStream outputStream = new FileOutputStream(walletFile);
        writeBinaryHeader(outputStream);

        OutputStreamWriter writer = new OutputStreamWriter(new DeflaterOutputStream(new ECIESOutputStream(outputStream, encryptionPubKey, getEncryptionMagic())), StandardCharsets.UTF_8);
        gson.toJson(wallet, writer);
        writer.close();
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

    public void backupWallet() throws IOException {
        backupWallet(null);
    }

    public void backupTempWallet() {
        try {
            backupWallet(TEMP_BACKUP_EXTENSION);
        } catch(IOException e) {
            log.error("Error creating ." + TEMP_BACKUP_EXTENSION + " backup wallet", e);
        }
    }

    public void backupWallet(String extension) throws IOException {
        File backupDir = getWalletsBackupDir();

        Date backupDate = new Date();
        String backupName = walletFile.getName();
        String dateSuffix = "-" + BACKUP_DATE_FORMAT.format(backupDate);
        int lastDot = backupName.lastIndexOf('.');
        if(lastDot > 0) {
            backupName = backupName.substring(0, lastDot) + dateSuffix + backupName.substring(lastDot);
        } else {
            backupName += dateSuffix;
        }

        if(extension != null) {
            backupName += "." + extension;
        }

        File backupFile = new File(backupDir, backupName);
        Files.copy(walletFile, backupFile);
    }

    public void deleteBackups() {
        deleteBackups(null);
    }

    public void deleteBackups(String extension) {
        File[] backups = getBackups(extension);
        for(File backup : backups) {
            backup.delete();
        }
    }

    private File[] getBackups(String extension) {
        return getBackups(extension, null);
    }

    private File[] getBackups(String extension, String notExtension) {
        File backupDir = getWalletsBackupDir();
        File[] backups = backupDir.listFiles((dir, name) -> {
            return name.startsWith(Files.getNameWithoutExtension(walletFile.getName()) + "-") &&
                    getBackupDate(name) != null &&
                    (extension == null || name.endsWith("." + extension)) &&
                    (notExtension == null || !name.endsWith("." + notExtension));
        });

        Arrays.sort(backups, Comparator.comparing(o -> getBackupDate(((File)o).getName())).reversed());

        return backups;
    }

    private String getBackupDate(String backupFileName) {
        Matcher matcher = DATE_PATTERN.matcher(backupFileName);
        if(matcher.matches()) {
            return matcher.group(1);
        }

        return null;
    }

    public ECKey getEncryptionPubKey() {
        return encryptionPubKey;
    }

    public void setEncryptionPubKey(ECKey encryptionPubKey) {
        this.encryptionPubKey = encryptionPubKey;
    }

    public ECKey getEncryptionKey(CharSequence password) throws IOException, StorageException {
        return getEncryptionKey(password, null);
    }

    private ECKey getEncryptionKey(CharSequence password, InputStream inputStream) throws IOException, StorageException {
        if(password.equals("")) {
            return NO_PASSWORD_KEY;
        }

        return getKeyDeriver(inputStream).deriveECKey(password);
    }

    public AsymmetricKeyDeriver getKeyDeriver() {
        return keyDeriver;
    }

    void setKeyDeriver(AsymmetricKeyDeriver keyDeriver) {
        this.keyDeriver = keyDeriver;
    }

    private AsymmetricKeyDeriver getKeyDeriver(InputStream inputStream) throws IOException, StorageException {
        if(keyDeriver == null) {
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

            keyDeriver = new Argon2KeyDeriver(salt);
        } else if(inputStream != null) {
            inputStream.skip(BINARY_HEADER_LENGTH);
        }

        return keyDeriver;
    }

    private static byte[] getEncryptionMagic() {
        return "BIE1".getBytes(StandardCharsets.UTF_8);
    }

    public static boolean walletExists(String walletName) {
        File encrypted = new File(getWalletsDir(), walletName.trim());
        File unencrypted = new File(getWalletsDir(), walletName.trim() + ".json");

        return (encrypted.exists() || unencrypted.exists());
    }

    public static File getExistingWallet(String walletName) {
        File encrypted = new File(getWalletsDir(), walletName);
        File unencrypted = new File(getWalletsDir(), walletName + ".json");

        if(encrypted.exists()) {
            return encrypted;
        } else if(unencrypted.exists()) {
            return unencrypted;
        }

        return null;
    }

    public static File getWalletFile(String walletName) {
        //TODO: Check for existing file
        return new File(getWalletsDir(), walletName);
    }

    public static File getWalletsBackupDir() {
        File walletsBackupDir = new File(getWalletsDir(), WALLETS_BACKUP_DIR);
        if(!walletsBackupDir.exists()) {
            walletsBackupDir.mkdirs();
        }

        return walletsBackupDir;
    }

    public static File getWalletsDir() {
        File walletsDir = new File(getSparrowDir(), WALLETS_DIR);
        if(!walletsDir.exists()) {
            walletsDir.mkdirs();
        }

        return walletsDir;
    }

    public static File getCertificateFile(String host) {
        File certsDir = getCertsDir();
        File[] certs = certsDir.listFiles((dir, name) -> name.equals(host));
        if(certs.length > 0) {
            return certs[0];
        }

        return null;
    }

    public static void saveCertificate(String host, Certificate cert) {
        try(FileWriter writer = new FileWriter(new File(getCertsDir(), host))) {
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write(Base64.getEncoder().encodeToString(cert.getEncoded()).replaceAll("(.{64})", "$1\n"));
            writer.write("\n-----END CERTIFICATE-----\n");
        } catch(CertificateEncodingException e) {
            log.error("Error encoding PEM certificate", e);
        } catch(IOException e) {
            log.error("Error writing PEM certificate", e);
        }
    }

    static File getCertsDir() {
        File certsDir = new File(getSparrowDir(), CERTS_DIR);
        if(!certsDir.exists()) {
            certsDir.mkdirs();
        }

        return certsDir;
    }

    static File getSparrowDir() {
        if(Network.get() != Network.MAINNET) {
            return new File(getSparrowHome(), Network.get().getName());
        }

        return getSparrowHome();
    }

    public static File getSparrowHome() {
        if(System.getProperty(MainApp.APP_HOME_PROPERTY) != null) {
            return new File(System.getProperty(MainApp.APP_HOME_PROPERTY));
        }

        if(Platform.getCurrent() == Platform.WINDOWS) {
            return new File(getHomeDir(), WINDOWS_SPARROW_DIR);
        }

        return new File(getHomeDir(), SPARROW_DIR);
    }

    static File getHomeDir() {
        if(Platform.getCurrent() == Platform.WINDOWS) {
            return new File(System.getenv("APPDATA"));
        }

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
            if(keystore.hasSeed()) {
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

    public static class WalletAndKey {
        public final Wallet wallet;
        public final Key key;

        public WalletAndKey(Wallet wallet, Key key) {
            this.wallet = wallet;
            this.key = key;
        }
    }

    public static class WalletBackupAndKey extends WalletAndKey {
        public final Wallet backupWallet;

        public WalletBackupAndKey(Wallet wallet, Wallet backupWallet, Key key) {
            super(wallet, key);
            this.backupWallet = backupWallet;
        }
    }

    public static class LoadWalletService extends Service<WalletBackupAndKey> {
        private final Storage storage;
        private final SecureString password;

        public LoadWalletService(Storage storage, SecureString password) {
            this.storage = storage;
            this.password = password;
        }

        @Override
        protected Task<WalletBackupAndKey> createTask() {
            return new Task<>() {
                protected WalletBackupAndKey call() throws IOException, StorageException {
                    WalletBackupAndKey walletBackupAndKey = storage.loadWallet(password);
                    password.clear();
                    return walletBackupAndKey;
                }
            };
        }
    }

    public static class KeyDerivationService extends Service<ECKey> {
        private final Storage storage;
        private final SecureString password;

        public KeyDerivationService(Storage storage, SecureString password) {
            this.storage = storage;
            this.password = password;
        }

        @Override
        protected Task<ECKey> createTask() {
            return new Task<>() {
                protected ECKey call() throws IOException, StorageException {
                    try {
                        return storage.getEncryptionKey(password);
                    } finally {
                        password.clear();
                    }
                }
            };
        }
    }

    public static class DecryptWalletService extends Service<Wallet> {
        private final Wallet wallet;
        private final SecureString password;

        public DecryptWalletService(Wallet wallet, SecureString password) {
            this.wallet = wallet;
            this.password = password;
        }

        @Override
        protected Task<Wallet> createTask() {
            return new Task<>() {
                protected Wallet call() throws IOException, StorageException {
                    try {
                        wallet.decrypt(password);
                        return wallet;
                    } finally {
                        password.clear();
                    }
                }
            };
        }
    }
}
