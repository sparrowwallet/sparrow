package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import dev.bwt.libbwt.daemon.CallbackNotifier;
import dev.bwt.libbwt.daemon.NativeBwtDaemon;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class Bwt {
    private static final Logger log = LoggerFactory.getLogger(Bwt.class);

    public static final String DEFAULT_CORE_WALLET = "sparrow";
    public static final String ELECTRUM_PORT = "0";
    private static final int IMPORT_BATCH_SIZE = 350;
    private static boolean initialized;
    private Long shutdownPtr;
    private boolean terminating;
    private boolean ready;

    public synchronized static void initialize() {
        if(!initialized) {
            try {
                org.controlsfx.tools.Platform platform = org.controlsfx.tools.Platform.getCurrent();
                String osArch = System.getProperty("os.arch");
                if(platform == org.controlsfx.tools.Platform.OSX && osArch.equals("aarch64")) {
                    NativeUtils.loadLibraryFromJar("/native/osx/aarch64/libbwt_jni.dylib");
                } else if(platform == org.controlsfx.tools.Platform.OSX) {
                    NativeUtils.loadLibraryFromJar("/native/osx/x64/libbwt_jni.dylib");
                } else if(platform == org.controlsfx.tools.Platform.WINDOWS) {
                    NativeUtils.loadLibraryFromJar("/native/windows/x64/bwt_jni.dll");
                } else if(osArch.equals("aarch64")) {
                    NativeUtils.loadLibraryFromJar("/native/linux/aarch64/libbwt_jni.so");
                } else {
                    NativeUtils.loadLibraryFromJar("/native/linux/x64/libbwt_jni.so");
                }
                initialized = true;
            } catch(IOException e) {
                log.error("Error loading bwt library", e);
            }
        }
    }

    private void start(CallbackNotifier callback) {
        start(Collections.emptyList(), Collections.emptyList(), null, null, null, callback);
    }

    private void start(Collection<Wallet> wallets, CallbackNotifier callback) {
        List<Wallet> validWallets = wallets.stream().filter(Wallet::isValid).collect(Collectors.toList());

        Set<String> outputDescriptors = new LinkedHashSet<>();
        Set<String> addresses = new LinkedHashSet<>();
        for(Wallet wallet : validWallets) {
            OutputDescriptor receiveOutputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.RECEIVE);
            outputDescriptors.add(receiveOutputDescriptor.toString(false, false));
            OutputDescriptor changeOutputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.CHANGE);
            outputDescriptors.add(changeOutputDescriptor.toString(false, false));

            if(wallet.isMasterWallet() && wallet.hasPaymentCode()) {
                Wallet notificationWallet = wallet.getNotificationWallet();
                WalletNode notificationNode = notificationWallet.getNode(KeyPurpose.NOTIFICATION);
                addresses.add(notificationNode.getAddress().toString());

                for(Wallet childWallet : wallet.getChildWallets()) {
                    if(childWallet.isNested()) {
                        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
                            for(WalletNode addressNode : childWallet.getNode(keyPurpose).getChildren()) {
                                addresses.add(addressNode.getAddress().toString());
                            }
                        }
                    }
                }
            }
        }

        int rescanSince = validWallets.stream().filter(wallet -> wallet.getBirthDate() != null).mapToInt(wallet -> (int)(wallet.getBirthDate().getTime() / 1000)).min().orElse(-1);
        int gapLimit = validWallets.stream().filter(wallet -> wallet.getGapLimit() > 0).mapToInt(Wallet::getGapLimit).max().orElse(Wallet.DEFAULT_LOOKAHEAD);

        boolean forceRescan = false;
        for(Wallet wallet : validWallets) {
            Date txBirthDate = wallet.getTransactions().values().stream().map(BlockTransactionHash::getDate).filter(Objects::nonNull).min(Date::compareTo).orElse(null);
            if((wallet.getBirthDate() != null && txBirthDate != null && wallet.getBirthDate().before(txBirthDate)) || (txBirthDate == null && wallet.getStoredBlockHeight() != null && wallet.getStoredBlockHeight() == 0)) {
                forceRescan = true;
            }
        }

        start(outputDescriptors, addresses, rescanSince, forceRescan, gapLimit, callback);
    }

    /**
     * Start the bwt daemon with the provided wallets
     * Blocks until the daemon is shut down.
     *
     * @param outputDescriptors descriptors of keys to add to Bitcoin Core
     * @param rescanSince seconds since epoch to start scanning keys
     * @param gapLimit desired gap limit beyond last used address
     * @param callback object receiving notifications
     */
    private void start(Collection<String> outputDescriptors, Collection<String> addresses, Integer rescanSince, Boolean forceRescan, Integer gapLimit, CallbackNotifier callback) {
        BwtConfig bwtConfig = new BwtConfig();
        bwtConfig.network = Network.get() == Network.MAINNET ? "bitcoin" : Network.getCanonical().getName();

        if(!outputDescriptors.isEmpty()) {
            bwtConfig.descriptors = outputDescriptors;
            bwtConfig.rescanSince = (rescanSince == null || rescanSince < 0 ? "now" : rescanSince);
            bwtConfig.forceRescan = forceRescan;
            bwtConfig.gapLimit = gapLimit;

            if(!addresses.isEmpty()) {
                bwtConfig.addresses = addresses;
            }
        } else {
            bwtConfig.requireAddresses = false;
            bwtConfig.bitcoindTimeout = 30;
        }

        bwtConfig.verbose = log.isDebugEnabled() ? 2 : 0;
        if(!log.isDebugEnabled()) {
            bwtConfig.setupLogger = false;
        }

        bwtConfig.electrumAddr = ElectrumServer.CORE_ELECTRUM_HOST + ":" + ELECTRUM_PORT;
        bwtConfig.electrumSkipMerkle = true;

        Config config = Config.get();
        if(config.getCoreServer() != null) {
            bwtConfig.bitcoindUrl = config.getCoreServer().getUrl();
            if(!config.getCoreServer().getHostAndPort().hasPort()) {
                bwtConfig.bitcoindUrl = config.getCoreServer().getUrl() + ":" + Network.get().getDefaultPort();
            }
            try {
                Protocol protocol = config.getCoreServer().getProtocol();
                HostAndPort hostAndPort = protocol.getServerHostAndPort(bwtConfig.bitcoindUrl);
                if(hostAndPort.getHost().endsWith(".local")) {
                    InetAddress inetAddress = InetAddress.getByName(hostAndPort.getHost());
                    bwtConfig.bitcoindUrl = protocol.toUrlString(inetAddress.getHostAddress(), hostAndPort.getPort());
                }
            } catch(Exception e) {
                //ignore
            }
        }

        HostAndPort torProxy = getTorProxy();
        if(Protocol.isOnionAddress(bwtConfig.bitcoindUrl) && torProxy != null) {
            bwtConfig.bitcoindProxy = torProxy.toString();
        }

        if((config.getCoreAuthType() == CoreAuthType.COOKIE || config.getCoreAuth() == null || config.getCoreAuth().length() < 2) && config.getCoreDataDir() != null) {
            bwtConfig.bitcoindDir = config.getCoreDataDir().getAbsolutePath() + "/";
        } else {
            bwtConfig.bitcoindAuth = config.getCoreAuth();
        }

        bwtConfig.bitcoindWallet = DEFAULT_CORE_WALLET;
        bwtConfig.createWalletIfMissing = true;

        Gson gson = new Gson();
        String jsonConfig = gson.toJson(bwtConfig);
        log.debug("Configuring bwt: " + jsonConfig);

        NativeBwtDaemon.start(jsonConfig, callback);
    }

    private HostAndPort getTorProxy() {
        return AppServices.isTorRunning() ?
                Tor.getDefault().getProxyHostAndPort() :
                (Config.get().getProxyServer() == null || Config.get().getProxyServer().isEmpty() || !Config.get().isUseProxy() ? null : HostAndPort.fromString(Config.get().getProxyServer().replace("localhost", "127.0.0.1")));
    }

    /**
     * Shut down the BWT daemon
     *
     */
    private void shutdown() {
        if(shutdownPtr == null) {
            terminating = true;
            return;
        }

        NativeBwtDaemon.shutdown(shutdownPtr);
        this.terminating = false;
        this.ready = false;
        this.shutdownPtr = null;
    }

    public boolean isRunning() {
        return shutdownPtr != null;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isTerminating() {
        return terminating;
    }

    public ConnectionService getConnectionService(boolean useWallets) {
        return new ConnectionService(useWallets);
    }

    public DisconnectionService getDisconnectionService() {
        return new DisconnectionService();
    }

    private static class BwtConfig {
        @SerializedName("network")
        public String network;

        @SerializedName("bitcoind_url")
        public String bitcoindUrl;

        @SerializedName("bitcoind_auth")
        public String bitcoindAuth;

        @SerializedName("bitcoind_dir")
        public String bitcoindDir;

        @SerializedName("bitcoind_cookie")
        public String bitcoindCookie;

        @SerializedName("bitcoind_wallet")
        public String bitcoindWallet;

        @SerializedName("bitcoind_proxy")
        public String bitcoindProxy;

        @SerializedName("bitcoind_timeout")
        public Integer bitcoindTimeout;

        @SerializedName("create_wallet_if_missing")
        public Boolean createWalletIfMissing;

        @SerializedName("descriptors")
        public Collection<String> descriptors;

        @SerializedName("addresses")
        public Collection<String> addresses;

        @SerializedName("xpubs")
        public String xpubs;

        @SerializedName("rescan_since")
        public Object rescanSince;

        @SerializedName("force_rescan")
        public Boolean forceRescan;

        @SerializedName("gap_limit")
        public Integer gapLimit;

        @SerializedName("initial_import_size")
        public Integer initialImportSize;

        @SerializedName("verbose")
        public Integer verbose;

        @SerializedName("electrum_addr")
        public String electrumAddr;

        @SerializedName("electrum_skip_merkle")
        public Boolean electrumSkipMerkle;

        @SerializedName("require_addresses")
        public Boolean requireAddresses;

        @SerializedName("setup_logger")
        public Boolean setupLogger;

        @SerializedName("http_addr")
        public String httpAddr;
    }

    public final class ConnectionService extends Service<Void> {
        private final boolean useWallets;

        public ConnectionService(boolean useWallets) {
            this.useWallets = useWallets;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                protected Void call() {
                    CallbackNotifier notifier = new CallbackNotifier() {
                        @Override
                        public void onBooting(long shutdownPtr) {
                            log.debug("Booting bwt");

                            Bwt.this.shutdownPtr = shutdownPtr;
                            if(terminating) {
                                Bwt.this.shutdown();
                                terminating = false;
                            } else {
                                Platform.runLater(() -> EventManager.get().post(new BwtBootStatusEvent("Connecting to Bitcoin Core node " + Config.get().getServerDisplayName() + "...")));
                            }
                        }

                        @Override
                        public void onSyncProgress(float progress, int tip) {
                            int percent = (int) (progress * 100.0);
                            Date tipDate = new Date((long)tip * 1000);
                            log.debug("Syncing " + percent + "%");
                            if(!terminating) {
                                Platform.runLater(() -> EventManager.get().post(new BwtSyncStatusEvent("Syncing" + (percent < 100 ? " (" + percent + "%)" : ""), percent, tipDate)));
                            }
                        }

                        @Override
                        public void onScanProgress(float progress, int remaining) {
                            int percent = (int) (progress * 100.0);
                            Duration remainingDuration = Duration.ofSeconds(remaining);
                            log.debug("Scanning " + percent + "%");
                            if(!terminating) {
                                Platform.runLater(() -> EventManager.get().post(new BwtScanStatusEvent("Scanning" + (percent < 100 ? " (" + percent + "%)" : ""), percent, remainingDuration)));
                            }
                        }

                        @Override
                        public void onElectrumReady(String addr) {
                            log.debug("Electrum ready");
                            if(!terminating) {
                                Platform.runLater(() -> EventManager.get().post(new BwtElectrumReadyStatusEvent("Electrum server ready", addr)));
                            }
                        }

                        @Override
                        public void onHttpReady(String addr) {
                            log.debug("http ready at " + addr);
                        }

                        @Override
                        public void onReady() {
                            log.debug("Bwt ready");
                            ready = true;
                            if(!terminating) {
                                Platform.runLater(() -> EventManager.get().post(new BwtReadyStatusEvent("Server ready")));
                            }
                        }
                    };

                    if(!useWallets) {
                        Bwt.this.start(notifier);
                    } else {
                        if(AppServices.get().getOpenWallets().keySet().stream().anyMatch(wallet -> wallet.getScriptType() == ScriptType.P2TR)) {
                            throw new IllegalStateException("Upgrade Bitcoin Core to v24 or later for Taproot wallet support");
                        }

                        Bwt.this.start(AppServices.get().getOpenWallets().keySet(), notifier);
                    }

                    return null;
                }
            };
        }
    }

    public final class DisconnectionService extends Service<Void> {
        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                protected Void call() {
                    Bwt.this.shutdown();
                    return null;
                }
            };
        }
    }
}
