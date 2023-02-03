package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.soroban.Soroban;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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

    public WalletAndKey loadUnencryptedWallet() throws IOException, StorageException {
        WalletAndKey masterWalletAndKey = persistence.loadWallet(this);
        encryptionPubKey = NO_PASSWORD_KEY;
        return migrateToDb(masterWalletAndKey);
    }

    public WalletAndKey loadEncryptedWallet(CharSequence password) throws IOException, StorageException {
        WalletAndKey masterWalletAndKey = persistence.loadWallet(this, password);
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

    public boolean isClosed() {
        return persistence.isClosed();
    }

    public void close() {
        ClosePersistenceService closePersistenceService = new ClosePersistenceService();
        closePersistenceService.start();
    }

    public void restorePublicKeysFromSeed(Wallet wallet, Key key) throws MnemonicException {
        checkWalletNetwork(wallet);

        if(wallet.containsMasterPrivateKeys()) {
            //Derive xpub and master fingerprint from seed, potentially with passphrase
            Wallet copy = wallet.copy(false);
            if(wallet.isEncrypted()) {
                if(key == null) {
                    throw new IllegalStateException("Wallet was not encrypted, but seed is");
                }

                copy.decrypt(key);
            }

            for(int i = 0; i < copy.getKeystores().size(); i++) {
                Keystore copyKeystore = copy.getKeystores().get(i);
                if(copyKeystore.hasSeed() && copyKeystore.getSeed().getPassphrase() == null) {
                    if(copyKeystore.getSeed().needsPassphrase()) {
                        if(!wallet.isMasterWallet() && wallet.getMasterWallet().getKeystores().size() == copy.getKeystores().size() && wallet.getMasterWallet().getKeystores().get(i).hasSeed()) {
                            copyKeystore.getSeed().setPassphrase(wallet.getMasterWallet().getKeystores().get(i).getSeed().getPassphrase());
                        } else {
                            Optional<String> optionalPassphrase = AppServices.getInteractionServices().requestPassphrase(wallet.getFullDisplayName(), copyKeystore);
                            if(optionalPassphrase.isPresent()) {
                                copyKeystore.getSeed().setPassphrase(optionalPassphrase.get());
                            } else {
                                return;
                            }
                        }
                    } else {
                        copyKeystore.getSeed().setPassphrase("");
                    }
                }
            }

            if(wallet.isWhirlpoolMasterWallet()) {
                String walletId = getWalletId(wallet);
                Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(walletId);
                whirlpool.setScode(wallet.getMasterMixConfig().getScode());
                whirlpool.setHDWallet(getWalletId(wallet), copy);
                Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
                soroban.setHDWallet(copy);
            }

            StandardAccount standardAccount = wallet.getStandardAccountType();
            if(standardAccount != null && standardAccount.getMinimumGapLimit() != null && wallet.gapLimit() == null) {
                wallet.setGapLimit(standardAccount.getMinimumGapLimit());
            }

            for(int i = 0; i < wallet.getKeystores().size(); i++) {
                Keystore keystore = wallet.getKeystores().get(i);
                if(keystore.hasSeed()) {
                    Keystore copyKeystore = copy.getKeystores().get(i);
                    Keystore derivedKeystore = Keystore.fromSeed(copyKeystore.getSeed(), copyKeystore.getKeyDerivation().getDerivation());
                    keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                    keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                    keystore.getSeed().setPassphrase(copyKeystore.getSeed().getPassphrase());
                    keystore.setBip47ExtendedPrivateKey(derivedKeystore.getBip47ExtendedPrivateKey());
                    copyKeystore.getSeed().clear();
                } else if(keystore.hasMasterPrivateExtendedKey()) {
                    Keystore copyKeystore = copy.getKeystores().get(i);
                    Keystore derivedKeystore = Keystore.fromMasterPrivateExtendedKey(copyKeystore.getMasterPrivateExtendedKey(), copyKeystore.getKeyDerivation().getDerivation());
                    keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                    keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                    keystore.setBip47ExtendedPrivateKey(derivedKeystore.getBip47ExtendedPrivateKey());
                    copyKeystore.getMasterPrivateKey().clear();
                }
            }
        }

        for(Wallet childWallet : wallet.getChildWallets()) {
            if(childWallet.isBip47()) {
                try {
                    Keystore masterKeystore = wallet.getKeystores().get(0);
                    Keystore keystore = childWallet.getKeystores().get(0);
                    keystore.setBip47ExtendedPrivateKey(masterKeystore.getBip47ExtendedPrivateKey());
                    List<ChildNumber> derivation = keystore.getKeyDerivation().getDerivation();
                    keystore.setKeyDerivation(new KeyDerivation(masterKeystore.getKeyDerivation().getMasterFingerprint(), derivation));
                    DeterministicKey pubKey = keystore.getBip47ExtendedPrivateKey().getKey().dropPrivateBytes().dropParent();
                    keystore.setExtendedPublicKey(new ExtendedKey(pubKey, keystore.getBip47ExtendedPrivateKey().getParentFingerprint(), derivation.get(derivation.size() - 1)));
                } catch(Exception e) {
                    log.error("Cannot prepare BIP47 keystore", e);
                }
            }
        }
    }

    private void checkWalletNetwork(Wallet wallet) {
        if(wallet.getNetwork() != null && wallet.getNetwork() != Network.get()) {
            throw new IllegalStateException("Provided " + wallet.getNetwork() + " wallet is invalid on a " + Network.get() + " network. Use a " + wallet.getNetwork() + " configuration to load this wallet.");
        }
    }

    public void backupWallet() throws IOException {
        if(walletFile.toPath().startsWith(getWalletsDir().toPath())) {
            backupWallet(null);
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

    public boolean delete() {
        deleteBackups();
        return IOUtils.secureDelete(walletFile);
    }

    public void deleteBackups() {
        deleteBackups(null);
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
            IOUtils.secureDelete(backup);
        }
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

    private WalletAndKey migrateToDb(WalletAndKey masterWalletAndKey) throws IOException, StorageException {
        if(getType() == PersistenceType.JSON) {
            log.info("Migrating " + masterWalletAndKey.getWallet().getName() + " from JSON to DB persistence");
            masterWalletAndKey = migrateType(PersistenceType.DB, masterWalletAndKey.getWallet(), masterWalletAndKey.getEncryptionKey());
        }

        return masterWalletAndKey;
    }

    private WalletAndKey migrateType(PersistenceType type, Wallet wallet, ECKey encryptionKey) throws IOException, StorageException {
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

        if(AppServices.get().getOpenWallets().keySet().stream().anyMatch(wallet -> walletName.equals(wallet.getName()))) {
            return true;
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
        if(System.getProperty(SparrowWallet.APP_HOME_PROPERTY) != null) {
            return new File(System.getProperty(SparrowWallet.APP_HOME_PROPERTY));
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

    public static class LoadWalletService extends Service<WalletAndKey> {
        private final Storage storage;
        private final SecureString password;

        private static Executor singleThreadedExecutor;

        public LoadWalletService(Storage storage) {
            this.storage = storage;
            this.password = null;
        }

        public LoadWalletService(Storage storage, SecureString password) {
            this.storage = storage;
            this.password = password;
        }

        @Override
        protected Task<WalletAndKey> createTask() {
            return new Task<>() {
                protected WalletAndKey call() throws IOException, StorageException {
                    WalletAndKey walletAndKey;

                    if(password != null) {
                        walletAndKey = storage.loadEncryptedWallet(password);
                        password.clear();
                    } else {
                        walletAndKey = storage.loadUnencryptedWallet();
                    }

                    return walletAndKey;
                }
            };
        }

        public static Executor getSingleThreadedExecutor() {
            if(singleThreadedExecutor == null) {
                BasicThreadFactory factory = new BasicThreadFactory.Builder().namingPattern("LoadWalletService-single").daemon(true).priority(Thread.MIN_PRIORITY).build();
                singleThreadedExecutor = Executors.newSingleThreadScheduledExecutor(factory);
            }

            return singleThreadedExecutor;
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

    public static class DeleteWalletService extends ScheduledService<Boolean> {
        private final Storage storage;

        public DeleteWalletService(Storage storage) {
            this.storage = storage;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() {
                    return storage.delete();
                }
            };
        }
    }
}
