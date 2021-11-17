package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.MainApp;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static final String TEMP_BACKUP_PREFIX = "tmp";
    public static final List<String> RESERVED_WALLET_NAMES = List.of("temp");

    private Persistence persistence;
    private File walletFile;
    private ECKey encryptionPubKey;

    public Storage(File walletFile) {
        this(!walletFile.exists() || walletFile.getName().endsWith("." + PersistenceType.DB.getExtension()) ? PersistenceType.DB : PersistenceType.JSON, walletFile);
    }

    public Storage(PersistenceType persistenceType, File walletFile) {
        this.persistence = persistenceType.getInstance();
        this.walletFile = walletFile;
    }

    public Storage(Persistence persistence, File walletFile) {
        this.persistence = persistence;
        this.walletFile = walletFile;
    }

    public File getWalletFile() {
        return walletFile;
    }

    public boolean isEncrypted() throws IOException {
        if(!walletFile.exists()) {
            return false;
        }

        return persistence.isEncrypted(walletFile);
    }

    public String getWalletId(Wallet wallet) {
        return persistence.getWalletId(this, wallet);
    }

    public String getWalletName(Wallet wallet) {
        return persistence.getWalletName(walletFile, wallet);
    }

    public String getWalletFileExtension() {
        if(walletFile.getName().endsWith("." + getType().getExtension())) {
            return getType().getExtension();
        }

        return "";
    }

    public WalletBackupAndKey loadUnencryptedWallet() throws IOException, StorageException {
        WalletBackupAndKey masterWalletAndKey = persistence.loadWallet(this);
        encryptionPubKey = NO_PASSWORD_KEY;
        return migrateToDb(masterWalletAndKey);
    }

    public WalletBackupAndKey loadEncryptedWallet(CharSequence password) throws IOException, StorageException {
        WalletBackupAndKey masterWalletAndKey = persistence.loadWallet(this, password);
        encryptionPubKey = ECKey.fromPublicOnly(masterWalletAndKey.getEncryptionKey());
        return migrateToDb(masterWalletAndKey);
    }

    public void saveWallet(Wallet wallet) throws IOException, StorageException {
        File parent = walletFile.getParentFile();
        if(!parent.exists() && !Storage.createOwnerOnlyDirectory(parent)) {
            throw new IOException("Could not create folder " + parent);
        }

        if(encryptionPubKey != null && !NO_PASSWORD_KEY.equals(encryptionPubKey)) {
            walletFile = persistence.storeWallet(this, wallet, encryptionPubKey);
            return;
        }

        walletFile = persistence.storeWallet(this, wallet);
    }

    public void updateWallet(Wallet wallet) throws IOException, StorageException {
        if(encryptionPubKey != null && !NO_PASSWORD_KEY.equals(encryptionPubKey)) {
            persistence.updateWallet(this, wallet, encryptionPubKey);
        } else {
            persistence.updateWallet(this, wallet);
        }
    }

    public boolean isPersisted(Wallet wallet) {
        return persistence.isPersisted(this, wallet);
    }

    public void close() {
        ClosePersistenceService closePersistenceService = new ClosePersistenceService();
        closePersistenceService.start();
    }

    public void backupWallet() throws IOException {
        if(walletFile.toPath().startsWith(getWalletsDir().toPath())) {
            backupWallet(null);
        }
    }

    public void backupTempWallet() {
        try {
            backupWallet(TEMP_BACKUP_PREFIX);
        } catch(IOException e) {
            log.error("Error creating " + TEMP_BACKUP_PREFIX + " backup wallet", e);
        }
    }

    private void backupWallet(String prefix) throws IOException {
        File backupDir = getWalletsBackupDir();

        Date backupDate = new Date();
        String walletName = persistence.getWalletName(walletFile, null);
        String dateSuffix = "-" + BACKUP_DATE_FORMAT.format(backupDate);
        String backupName = walletName + dateSuffix + walletFile.getName().substring(walletName.length());

        if(prefix != null) {
            backupName = prefix + "_" + backupName;
        }

        File backupFile = new File(backupDir, backupName);
        if(!backupFile.exists()) {
            createOwnerOnlyFile(backupFile);
        }

        try(FileOutputStream outputStream = new FileOutputStream(backupFile)) {
            copyWallet(outputStream);
        }
    }

    public void copyWallet(OutputStream outputStream) throws IOException {
        persistence.copyWallet(walletFile, outputStream);
    }

    public void deleteBackups() {
        deleteBackups(null);
    }

    public void deleteTempBackups(boolean forceSave) {
        File[] backups = getBackups(Storage.TEMP_BACKUP_PREFIX);
        if(backups.length > 0 && (forceSave || hasStartedSince(backups[0]))) {
            File permanent = new File(backups[0].getParent(), backups[0].getName().substring(Storage.TEMP_BACKUP_PREFIX.length() + 1));
            backups[0].renameTo(permanent);
        }

        deleteBackups(Storage.TEMP_BACKUP_PREFIX);
    }

    private boolean hasStartedSince(File lastBackup) {
        try {
            Date date = BACKUP_DATE_FORMAT.parse(getBackupDate(lastBackup.getName()));
            ProcessHandle.Info processInfo = ProcessHandle.current().info();
            return (processInfo.startInstant().isPresent() && processInfo.startInstant().get().isAfter(date.toInstant()));
        } catch(Exception e) {
            log.error("Error parsing date for backup file " + lastBackup.getName(), e);
            return false;
        }
    }

    private void deleteBackups(String prefix) {
        File[] backups = getBackups(prefix);
        for(File backup : backups) {
            backup.delete();
        }
    }

    public File getTempBackup() {
        File[] backups = getBackups(TEMP_BACKUP_PREFIX);
        return backups.length == 0 ? null : backups[0];
    }

    File[] getBackups(String prefix) {
        File backupDir = getWalletsBackupDir();
        String walletName = persistence.getWalletName(walletFile, null);
        String extension = walletFile.getName().substring(walletName.length());
        File[] backups = backupDir.listFiles((dir, name) -> {
            return name.startsWith((prefix == null ? "" : prefix + "_") + walletName + "-") &&
                    getBackupDate(name) != null &&
                    (extension.isEmpty() || name.endsWith(extension));
        });

        backups = backups == null ? new File[0] : backups;
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

    private WalletBackupAndKey migrateToDb(WalletBackupAndKey masterWalletAndKey) throws IOException, StorageException {
        if(getType() == PersistenceType.JSON) {
            log.info("Migrating " + masterWalletAndKey.getWallet().getName() + " from JSON to DB persistence");
            masterWalletAndKey = migrateType(PersistenceType.DB, masterWalletAndKey.getWallet(), masterWalletAndKey.getEncryptionKey());
        }

        return masterWalletAndKey;
    }

    private WalletBackupAndKey migrateType(PersistenceType type, Wallet wallet, ECKey encryptionKey) throws IOException, StorageException {
        File existingFile = walletFile;

        try {
            AsymmetricKeyDeriver keyDeriver = persistence.getKeyDeriver();
            persistence = type.getInstance();
            persistence.setKeyDeriver(keyDeriver);
            walletFile = new File(walletFile.getParentFile(), wallet.getName() + "." + type.getExtension());
            if(walletFile.exists()) {
                walletFile.delete();
            }

            saveWallet(wallet);
            if(type == PersistenceType.DB) {
                for(Wallet childWallet : wallet.getChildWallets()) {
                    saveWallet(childWallet);
                }
            }

            if(NO_PASSWORD_KEY.equals(encryptionPubKey)) {
                return persistence.loadWallet(this);
            }

            return persistence.loadWallet(this, null, encryptionKey);
        } catch(Exception e) {
            existingFile = null;
            throw e;
        } finally {
            if(existingFile != null) {
                existingFile.delete();
            }
        }
    }

    public ECKey getEncryptionPubKey() {
        return encryptionPubKey;
    }

    public void setEncryptionPubKey(ECKey encryptionPubKey) {
        this.encryptionPubKey = encryptionPubKey;
    }

    public ECKey getEncryptionKey(CharSequence password) throws IOException, StorageException {
        return persistence.getEncryptionKey(password);
    }

    public AsymmetricKeyDeriver getKeyDeriver() {
        return persistence.getKeyDeriver();
    }

    void setKeyDeriver(AsymmetricKeyDeriver keyDeriver) {
        persistence.setKeyDeriver(keyDeriver);
    }

    public PersistenceType getType() {
        return persistence.getType();
    }

    public static boolean walletExists(String walletName) {
        File encrypted = new File(getWalletsDir(), walletName.trim());
        if(encrypted.exists()) {
            return true;
        }

        for(PersistenceType persistenceType : PersistenceType.values()) {
            File unencrypted = new File(getWalletsDir(), walletName.trim() + "." + persistenceType.getExtension());
            if(unencrypted.exists()) {
                return true;
            }
        }

        return RESERVED_WALLET_NAMES.contains(walletName);
    }

    public static File getExistingWallet(String walletName) {
        return getExistingWallet(getWalletsDir(), walletName);
    }

    public static File getExistingWallet(File walletsDir, String walletName) {
        File encrypted = new File(walletsDir, walletName.trim());
        if(encrypted.exists()) {
            return encrypted;
        }

        for(PersistenceType persistenceType : PersistenceType.values()) {
            File unencrypted = new File(walletsDir, walletName.trim() + "." + persistenceType.getExtension());
            if(unencrypted.exists()) {
                return unencrypted;
            }
        }

        return null;
    }

    public static File getWalletFile(String walletName) {
        //TODO: Check for existing file
        return new File(getWalletsDir(), walletName);
    }

    public static boolean isWalletFile(File walletFile) {
        for(PersistenceType type : PersistenceType.values()) {
            if(walletFile.getName().endsWith("." + type.getExtension())) {
                return true;
            }

            try {
                if(type == PersistenceType.JSON && type.getInstance().isEncrypted(walletFile)) {
                    return true;
                }
            } catch(IOException e) {
                //ignore
            }
        }

        return false;
    }

    public static boolean isEncrypted(File walletFile) {
        try {
            for(PersistenceType type : PersistenceType.values()) {
                if(walletFile.getName().endsWith("." + type.getExtension())) {
                    return type.getInstance().isEncrypted(walletFile);
                }
            }

            PersistenceType detectedType = detectPersistenceType(walletFile);
            if(detectedType != null) {
                return detectedType.getInstance().isEncrypted(walletFile);
            }
        } catch(IOException e) {
            //ignore
        }

        return FileType.BINARY.equals(IOUtils.getFileType(walletFile));
    }

    public static PersistenceType detectPersistenceType(File walletFile) {
        try(Reader reader = new FileReader(walletFile, StandardCharsets.UTF_8)) {
            int firstChar = reader.read();

            if(firstChar == 'U' || firstChar == '{') {
                return PersistenceType.JSON;
            }

            if(firstChar == 'H') {
                return PersistenceType.DB;
            }
        } catch(IOException e) {
            log.error("Error detecting persistence type", e);
        }

        return null;
    }

    public static File getWalletsBackupDir() {
        File walletsBackupDir = new File(getWalletsDir(), WALLETS_BACKUP_DIR);
        if(!walletsBackupDir.exists()) {
            createOwnerOnlyDirectory(walletsBackupDir);
        }

        return walletsBackupDir;
    }

    public static File getWalletsDir() {
        File walletsDir = new File(getSparrowDir(), WALLETS_DIR);
        if(!walletsDir.exists()) {
            createOwnerOnlyDirectory(walletsDir);
        }

        return walletsDir;
    }

    public static File getCertificateFile(String host) {
        File certsDir = getCertsDir();
        File[] certs = certsDir.listFiles((dir, name) -> name.equals(host));
        if(certs != null && certs.length > 0) {
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
            createOwnerOnlyDirectory(certsDir);
        }

        return certsDir;
    }

    static File getSparrowDir() {
        File sparrowDir;
        if(Network.get() != Network.MAINNET) {
            sparrowDir = new File(getSparrowHome(), Network.get().getName());
        } else {
            sparrowDir = getSparrowHome();
        }

        if(!sparrowDir.exists()) {
            createOwnerOnlyDirectory(sparrowDir);
        }

        return sparrowDir;
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

    public static boolean createOwnerOnlyDirectory(File directory) {
        try {
            if(Platform.getCurrent() == Platform.WINDOWS) {
                Files.createDirectories(directory.toPath());
                return true;
            }

            Files.createDirectories(directory.toPath(), PosixFilePermissions.asFileAttribute(getDirectoryOwnerOnlyPosixFilePermissions()));
            return true;
        } catch(UnsupportedOperationException e) {
            return directory.mkdirs();
        } catch(IOException e) {
            log.error("Could not create directory " + directory.getAbsolutePath(), e);
        }

        return false;
    }

    public static boolean createOwnerOnlyFile(File file) {
        try {
            if(Platform.getCurrent() == Platform.WINDOWS) {
                Files.createFile(file.toPath());
                return true;
            }

            Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(getFileOwnerOnlyPosixFilePermissions()));
            return true;
        } catch(UnsupportedOperationException e) {
            return false;
        } catch(IOException e) {
            log.error("Could not create file " + file.getAbsolutePath(), e);
        }

        return false;
    }

    private static Set<PosixFilePermission> getDirectoryOwnerOnlyPosixFilePermissions() {
        Set<PosixFilePermission> ownerOnly = getFileOwnerOnlyPosixFilePermissions();
        ownerOnly.add(PosixFilePermission.OWNER_EXECUTE);

        return ownerOnly;
    }

    private static Set<PosixFilePermission> getFileOwnerOnlyPosixFilePermissions() {
        Set<PosixFilePermission> ownerOnly = EnumSet.noneOf(PosixFilePermission.class);
        ownerOnly.add(PosixFilePermission.OWNER_READ);
        ownerOnly.add(PosixFilePermission.OWNER_WRITE);

        return ownerOnly;
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
                    WalletBackupAndKey walletBackupAndKey = storage.loadEncryptedWallet(password);
                    password.clear();
                    return walletBackupAndKey;
                }
            };
        }
    }

    public static class KeyDerivationService extends Service<ECKey> {
        private final Storage storage;
        private final SecureString password;
        private final boolean verifyPassword;

        public KeyDerivationService(Storage storage, SecureString password) {
            this.storage = storage;
            this.password = password;
            this.verifyPassword = false;
        }

        public KeyDerivationService(Storage storage, SecureString password, boolean verifyPassword) {
            this.storage = storage;
            this.password = password;
            this.verifyPassword = verifyPassword;
        }

        @Override
        protected Task<ECKey> createTask() {
            return new Task<>() {
                protected ECKey call() throws IOException, StorageException {
                    try {
                        ECKey encryptionFullKey = storage.getEncryptionKey(password);
                        if(verifyPassword && !ECKey.fromPublicOnly(encryptionFullKey).equals(storage.getEncryptionPubKey())) {
                            throw new InvalidPasswordException("Derived pubkey does not match stored pubkey");
                        }

                        return encryptionFullKey;
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

    public class ClosePersistenceService extends Service<Void> {
        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                protected Void call() {
                    persistence.close();
                    return null;
                }
            };
        }
    }
}
