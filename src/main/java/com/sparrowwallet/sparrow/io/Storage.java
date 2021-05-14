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
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.text.DateFormat;
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
    public static final String TEMP_BACKUP_EXTENSION = "tmp";

    private final Persistence persistence;
    private File walletFile;
    private ECKey encryptionPubKey;

    public Storage(File walletFile) {
        this.persistence = new JsonPersistence();
        this.walletFile = walletFile;
    }

    public File getWalletFile() {
        return walletFile;
    }

    public WalletBackupAndKey loadUnencryptedWallet() throws IOException, StorageException {
        Wallet wallet = persistence.loadWallet(walletFile);
        Wallet backupWallet = loadBackupWallet(null);
        Map<Storage, WalletBackupAndKey> childWallets = persistence.loadChildWallets(walletFile, wallet, null);

        encryptionPubKey = NO_PASSWORD_KEY;
        return new WalletBackupAndKey(wallet, backupWallet, null, null, childWallets);
    }

    public WalletBackupAndKey loadEncryptedWallet(CharSequence password) throws IOException, StorageException {
        WalletBackupAndKey masterWalletAndKey = persistence.loadWallet(walletFile, password);
        Wallet backupWallet = loadBackupWallet(masterWalletAndKey.getEncryptionKey());
        Map<Storage, WalletBackupAndKey> childWallets = persistence.loadChildWallets(walletFile, masterWalletAndKey.getWallet(), masterWalletAndKey.getEncryptionKey());

        encryptionPubKey = ECKey.fromPublicOnly(masterWalletAndKey.getEncryptionKey());
        return new WalletBackupAndKey(masterWalletAndKey.getWallet(), backupWallet, masterWalletAndKey.getEncryptionKey(), persistence.getKeyDeriver(), childWallets);
    }

    protected Wallet loadBackupWallet(ECKey encryptionKey) throws IOException, StorageException {
        Map<File, Wallet> backupWallets;
        if(encryptionKey != null) {
            File[] backups = getBackups(TEMP_BACKUP_EXTENSION, persistence.getType().getExtension() + "." + TEMP_BACKUP_EXTENSION);
            backupWallets = persistence.loadWallets(backups, encryptionKey);
            return backupWallets.isEmpty() ? null : backupWallets.values().iterator().next();
        } else {
            File[] backups = getBackups(persistence.getType().getExtension() + "." + TEMP_BACKUP_EXTENSION);
            backupWallets = persistence.loadWallets(backups, null);
        }

        return backupWallets.isEmpty() ? null : backupWallets.values().iterator().next();
    }

    public void saveWallet(Wallet wallet) throws IOException {
        if(encryptionPubKey != null && !NO_PASSWORD_KEY.equals(encryptionPubKey)) {
            walletFile = persistence.storeWallet(walletFile, wallet, encryptionPubKey);
            return;
        }

        walletFile = persistence.storeWallet(walletFile, wallet);
    }

    public void backupWallet() throws IOException {
        if(walletFile.toPath().startsWith(getWalletsDir().toPath())) {
            backupWallet(null);
        }
    }

    public void backupTempWallet() {
        try {
            backupWallet(TEMP_BACKUP_EXTENSION);
        } catch(IOException e) {
            log.error("Error creating ." + TEMP_BACKUP_EXTENSION + " backup wallet", e);
        }
    }

    private void backupWallet(String extension) throws IOException {
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
        if(!backupFile.exists()) {
            createOwnerOnlyFile(backupFile);
        }
        com.google.common.io.Files.copy(walletFile, backupFile);
    }

    public void deleteBackups() {
        deleteBackups(null);
    }

    public void deleteTempBackups() {
        deleteBackups(Storage.TEMP_BACKUP_EXTENSION);
    }

    private void deleteBackups(String extension) {
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
            return name.startsWith(com.google.common.io.Files.getNameWithoutExtension(walletFile.getName()) + "-") &&
                    getBackupDate(name) != null &&
                    (extension == null || name.endsWith("." + extension)) &&
                    (notExtension == null || !name.endsWith("." + notExtension));
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

        return false;
    }

    public static File getExistingWallet(String walletName) {
        File encrypted = new File(getWalletsDir(), walletName.trim());
        if(encrypted.exists()) {
            return encrypted;
        }

        for(PersistenceType persistenceType : PersistenceType.values()) {
            File unencrypted = new File(getWalletsDir(), walletName.trim() + "." + persistenceType.getExtension());
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
