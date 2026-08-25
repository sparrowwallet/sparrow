package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.*;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.SparrowWallet;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Storage {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);
    public static final ECKey NO_PASSWORD_KEY = ECKey.fromPublicOnly(ECKey.fromPrivate(Utils.hexToBytes("885e5a09708a167ea356a252387aa7c4893d138d632e296df8fbf5c12798bd28")));

    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> warnedDirectories = ConcurrentHashMap.newKeySet();

    public static final String WALLETS_DIR = "wallets";
    public static final String WALLETS_BACKUP_DIR = "backup";
    public static final String CERTS_DIR = "certs";
    public static final String HEADERS_DIR = "headers";
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

    public void closeAndWait() {
        persistence.close();
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

            StandardAccount standardAccount = wallet.getStandardAccountType();
            if(standardAccount != null && standardAccount.getMinimumGapLimit() != null && wallet.gapLimit() == null) {
                wallet.setGapLimit(standardAccount.getMinimumGapLimit());
            }

            for(int i = 0; i < wallet.getKeystores().size(); i++) {
                Keystore keystore = wallet.getKeystores().get(i);
                if(keystore.hasSeed()) {
                    Keystore copyKeystore = copy.getKeystores().get(i);
                    Keystore derivedKeystore = Keystore.fromSeed(copyKeystore.getSeed(), wallet.getPolicyType(), copyKeystore.getKeyDerivation().getDerivation());
                    keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                    keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                    keystore.getSeed().setPassphrase(copyKeystore.getSeed().getPassphrase());
                    keystore.setBip47ExtendedPrivateKey(derivedKeystore.getBip47ExtendedPrivateKey());
                    keystore.setSilentPaymentScanAddress(derivedKeystore.getSilentPaymentScanAddress());
                    copyKeystore.getSeed().clear();
                } else if(keystore.hasMasterPrivateExtendedKey()) {
                    Keystore copyKeystore = copy.getKeystores().get(i);
                    Keystore derivedKeystore = Keystore.fromMasterPrivateExtendedKey(copyKeystore.getMasterPrivateExtendedKey(), wallet.getPolicyType(), copyKeystore.getKeyDerivation().getDerivation());
                    keystore.setKeyDerivation(derivedKeystore.getKeyDerivation());
                    keystore.setExtendedPublicKey(derivedKeystore.getExtendedPublicKey());
                    keystore.setBip47ExtendedPrivateKey(derivedKeystore.getBip47ExtendedPrivateKey());
                    keystore.setSilentPaymentScanAddress(derivedKeystore.getSilentPaymentScanAddress());
                    copyKeystore.getMasterPrivateExtendedKey().clear();
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
        if(wallet.getNetwork() != null && wallet.getNetwork() != Network.getCanonical()) {
            throw new IllegalStateException("Provided " + wallet.getNetwork() + " wallet is invalid on a " + Network.getCanonical() + " network. Use a " + wallet.getNetwork() + " configuration to load this wallet.");
        }
    }

    public void backupWallet() throws IOException {
        if(walletFile.toPath().startsWith(getWalletsDir().toPath())) {
            backupWallet(null);
        }
    }

    private void backupWallet(String prefix) throws IOException {
        File backupDir = getWalletsBackupDir();

        String walletName = persistence.getWalletName(walletFile, null);
        String dateSuffix = "-" + BACKUP_DATE_FORMAT.format(LocalDateTime.now());
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

    public boolean delete(boolean deleteBackups) {
        if(deleteBackups) {
            deleteBackups();
        }

        return IOUtils.secureDelete(walletFile);
    }

    public void deleteBackups() {
        deleteBackups(null);
    }

    private void deleteBackups(String prefix) {
        File[] backups = getBackups(prefix);
        for(File backup : backups) {
            IOUtils.secureDelete(backup);
        }
    }

    File[] getBackups(String prefix) {
        return getBackups(getWalletsBackupDir(), prefix);
    }

    File[] getBackups(File backupDir, String prefix) {
        String walletName = persistence.getWalletName(walletFile, null);
        String extension = walletFile.getName().substring(walletName.length());
        Pattern backupPattern = Pattern.compile(Pattern.quote((prefix == null ? "" : prefix + "_") + walletName + "-") + "[0-9]{14}" + Pattern.quote(extension));
        File[] backups = backupDir.listFiles((dir, name) -> backupPattern.matcher(name).matches());

        backups = backups == null ? new File[0] : backups;
        Arrays.sort(backups, Comparator.comparing(File::getName).reversed());

        return backups;
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
        } else {
            //Unlike the wallets directory below, this directory is always created by Sparrow, so restricting it restores the permissions it was created with
            setOwnerOnlyDirectory(walletsBackupDir);
        }

        return walletsBackupDir;
    }

    public static File getWalletsDir() {
        boolean defaultWalletsDir = false;
        File walletsDir = Config.get().getWalletsDir();
        if(walletsDir != null) {
            if(!walletsDir.exists() && (walletsDir.getParentFile() == null || !walletsDir.getParentFile().exists() || !walletsDir.getParentFile().canWrite())) {
                log.info("Configured wallets directory " + walletsDir.getAbsolutePath() + " is not reachable, reverting to default");
                walletsDir = null;
            }
        }
        if(walletsDir == null) {
            walletsDir = new File(getDataDir(), WALLETS_DIR);
            defaultWalletsDir = true;
        }
        if(!walletsDir.exists()) {
            createOwnerOnlyDirectory(walletsDir);
        } else if(defaultWalletsDir) {
            setOwnerOnlyDirectory(walletsDir);
        }

        return walletsDir;
    }

    public static File getCertificateFile(String host) {
        return findCertFile(getCertName(host));
    }

    public static void saveCertificate(String host, Certificate cert) {
        writeCertPem(getCertName(host), cert);
    }

    public static File getCaCertificateFile(String host) {
        return findCertFile(host + ".cacert");
    }

    public static void saveCaCertificate(String host, Certificate cert) {
        writeCertPem(host + ".cacert", cert);
    }

    private static File findCertFile(String filename) {
        File[] certs = getCertsDir().listFiles((dir, name) -> name.equals(filename));
        if(certs != null && certs.length > 0) {
            return certs[0];
        }

        return null;
    }

    private static void writeCertPem(String filename, Certificate cert) {
        try(FileWriter writer = new FileWriter(new File(getCertsDir(), filename))) {
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write(Base64.getEncoder().encodeToString(cert.getEncoded()).replaceAll("(.{64})", "$1\n"));
            writer.write("\n-----END CERTIFICATE-----\n");
        } catch(CertificateEncodingException e) {
            log.error("Error encoding PEM certificate", e);
        } catch(IOException e) {
            log.error("Error writing PEM certificate", e);
        }
    }

    private static String getCertName(String host) {
        if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
            return host + ".bitcoind";
        }

        return host;
    }

    static File getCertsDir() {
        File certsDir = new File(getDataDir(), CERTS_DIR);
        if(!certsDir.exists()) {
            createOwnerOnlyDirectory(certsDir);
        }

        return certsDir;
    }

    /**
     * Returns the network specific directory containing the verified block headers, which are regenerable and so kept with the other caches.
     */
    public static File getHeadersDir() {
        File headersDir = new File(getCacheDir(), HEADERS_DIR);
        if(!headersDir.exists()) {
            createOwnerOnlyDirectory(headersDir);
        }

        return headersDir;
    }

    /**
     * Returns the network specific directory containing the configuration file.
     */
    public static File getConfigDir() {
        return getNetworkDir(getConfigHome());
    }

    /**
     * Returns the network specific directory containing wallets and certificates.
     */
    public static File getDataDir() {
        return getNetworkDir(getDataHome());
    }

    /**
     * Returns the network specific directory containing regenerable files.
     */
    public static File getCacheDir() {
        return getNetworkDir(getCacheHome());
    }

    /**
     * Returns the network specific directory containing logs and runtime state.
     */
    public static File getStateDir() {
        return getNetworkDir(getStateHome());
    }

    public static File getConfigHome() {
        return ApplicationDir.CONFIG.get(SparrowWallet.APP_NAME);
    }

    public static File getDataHome() {
        return ApplicationDir.DATA.get(SparrowWallet.APP_NAME);
    }

    public static File getCacheHome() {
        return ApplicationDir.CACHE.get(SparrowWallet.APP_NAME);
    }

    public static File getStateHome() {
        return getStateHome(false);
    }

    public static File getStateHome(boolean useDefault) {
        return ApplicationDir.STATE.get(SparrowWallet.APP_NAME, useDefault);
    }

    /**
     * Returns the single directory used when the XDG Base Directory Specification is not followed, ignoring any configured home.
     *
     * Provides a fixed location that does not move as categories are migrated, and is where earlier versions wrote all of their files.
     */
    public static File getDefaultHome() {
        return ApplicationDir.getDefaultDir(SparrowWallet.APP_NAME);
    }

    /**
     * Resolves the network specific directory within the given application directory, creating it if necessary.
     *
     * Where a network has been renamed, any existing directory under the previous name is moved and replaced with a symlink,
     * and a symlink under the previous name is otherwise maintained for convenience.
     */
    private static File getNetworkDir(File applicationDir) {
        File networkDir;
        Network network = Network.get();
        if(network != Network.MAINNET) {
            networkDir = new File(applicationDir, network.getHome());
            if(!network.getName().equals(network.getHome()) && !networkDir.exists()) {
                File networkNameDir = new File(applicationDir, network.getName());
                if(networkNameDir.exists() && networkNameDir.isDirectory() && !Files.isSymbolicLink(networkNameDir.toPath())) {
                    try {
                        if(networkNameDir.renameTo(networkDir) && !isWindows()) {
                            Files.createSymbolicLink(networkNameDir.toPath(), Path.of(networkDir.getName()));
                        }
                    } catch(Exception e) {
                        log.debug("Error creating symlink from " + networkNameDir.getAbsolutePath() + " to " + networkDir.getName(), e);
                    }
                }
            }
        } else {
            networkDir = applicationDir;
        }

        if(!networkDir.exists()) {
            createOwnerOnlyDirectory(networkDir);
        }

        if(!network.getName().equals(network.getHome()) && !isWindows()) {
            try {
                Path networkNamePath = applicationDir.toPath().resolve(network.getName());
                if(Files.isSymbolicLink(networkNamePath)) {
                    Path symlinkTarget = applicationDir.toPath().resolve(Files.readSymbolicLink(networkNamePath));
                    if(!Files.isSameFile(networkDir.toPath(), symlinkTarget)) {
                        Files.delete(networkNamePath);
                        Files.createSymbolicLink(networkNamePath, Path.of(networkDir.getName()));
                    }
                } else if(!Files.exists(networkNamePath)) {
                    Files.createSymbolicLink(networkNamePath, Path.of(networkDir.getName()));
                }
            } catch(Exception e) {
                log.debug("Error updating symlink from " + network.getName() + " to " + networkDir.getName(), e);
            }
        }

        return networkDir;
    }

    /**
     * Logs the application directories in use where they do not all resolve to the default application directory.
     */
    public static void logApplicationDirs() {
        List<ApplicationDir> xdgDirs = Arrays.stream(ApplicationDir.values()).filter(applicationDir -> applicationDir.isXdg(SparrowWallet.APP_NAME)).toList();
        if(!xdgDirs.isEmpty() && log.isInfoEnabled()) {
            log.info("Using XDG base directories for " + xdgDirs.stream().map(applicationDir -> applicationDir.toString().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")) +
                    " (config: " + getConfigHome() + ", data: " + getDataHome() + ", cache: " + getCacheHome() + ", state: " + getStateHome() + ")");
        }
    }

    public static boolean createOwnerOnlyDirectory(File directory) {
        try {
            if(isWindows()) {
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

    public static void setOwnerOnlyDirectory(File directory) {
        //A symlinked directory has a target outside the application directories that may be deliberately shared, so leave it alone
        if(isWindows() || Files.isSymbolicLink(directory.toPath())) {
            return;
        }

        Set<PosixFilePermission> ownerOnly = getDirectoryOwnerOnlyPosixFilePermissions();
        Set<PosixFilePermission> currentPermissions;
        try {
            currentPermissions = Files.getPosixFilePermissions(directory.toPath());
        } catch(UnsupportedOperationException | IOException e) {
            log.debug("Could not read permissions on directory " + directory.getAbsolutePath(), e);
            return;
        }

        if(!ownerOnly.equals(currentPermissions)) {
            try {
                Files.setPosixFilePermissions(directory.toPath(), ownerOnly);
            } catch(IOException e) {
                if(warnedDirectories.add(directory.getAbsolutePath())) {
                    log.warn("Could not restrict permissions on directory " + directory.getAbsolutePath() + ", it remains readable by other users", e);
                }
            }
        }
    }

    public static boolean createOwnerOnlyFile(File file) {
        try {
            if(isWindows()) {
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

    private static boolean isWindows() {
        return OsType.getCurrent() == OsType.WINDOWS;
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
                BasicThreadFactory factory = BasicThreadFactory.builder().namingPattern("LoadWalletService-single").daemon(true).priority(Thread.MIN_PRIORITY).build();
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

    public static class CopyWalletService extends Service<Void> {
        private final Wallet wallet;
        private final File newWalletFile;

        public CopyWalletService(Wallet wallet, File newWalletFile) {
            this.wallet = wallet;
            this.newWalletFile = newWalletFile;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                protected Void call() throws IOException, ExportException {
                    Sparrow export = new Sparrow();
                    try(BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(newWalletFile))) {
                        export.exportWallet(wallet, outputStream, null);
                    }

                    return null;
                }
            };
        }
    }

    public static class DeleteWalletService extends ScheduledService<Boolean> {
        private final Storage storage;
        private final boolean deleteBackups;

        public DeleteWalletService(Storage storage, boolean deleteBackups) {
            this.storage = storage;
            this.deleteBackups = deleteBackups;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() {
                    return storage.delete(deleteBackups);
                }
            };
        }
    }
}
