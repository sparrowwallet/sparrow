package com.sparrowwallet.sparrow.net;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import dev.bwt.daemon.CallbackNotifier;
import dev.bwt.daemon.NativeBwtDaemon;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class Bwt {
    private static final Logger log = LoggerFactory.getLogger(Bwt.class);
    private Long shutdownPtr;

    static {
        try {
            org.controlsfx.tools.Platform platform = org.controlsfx.tools.Platform.getCurrent();
            if(platform == org.controlsfx.tools.Platform.OSX) {
                NativeUtils.loadLibraryFromJar("/native/osx/x64/libbwt.dylib");
            } else if(platform == org.controlsfx.tools.Platform.WINDOWS) {
                NativeUtils.loadLibraryFromJar("/native/windows/x64/bwt.dll");
            } else {
                NativeUtils.loadLibraryFromJar("/native/linux/x64/libbwt.so");
            }
        } catch(IOException e) {
            log.error("Error loading bwt library", e);
        }
    }

    private void start(CallbackNotifier callback) {
        List<String> descriptors = List.of("pkh(02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5)");
        Date now = new Date();
        start(descriptors, (int)(now.getTime() / 1000), null, callback);
    }

    private void start(Collection<Wallet> wallets, CallbackNotifier callback) {
        List<String> outputDescriptors = new ArrayList<>();
        for(Wallet wallet : wallets) {
            OutputDescriptor receiveOutputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.RECEIVE);
            outputDescriptors.add(receiveOutputDescriptor.toString(false, false));
            OutputDescriptor changeOutputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.CHANGE);
            outputDescriptors.add(changeOutputDescriptor.toString(false, false));
        }

        int rescanSince = wallets.stream().filter(wallet -> wallet.getBirthDate() != null).mapToInt(wallet -> (int)(wallet.getBirthDate().getTime() / 1000)).min().orElse(0);
        int gapLimit = wallets.stream().filter(wallet -> wallet.getGapLimit() > 0).mapToInt(Wallet::getGapLimit).max().orElse(Wallet.DEFAULT_LOOKAHEAD);

        start(outputDescriptors, rescanSince, gapLimit, callback);
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
    private void start(List<String> outputDescriptors, Integer rescanSince, Integer gapLimit, CallbackNotifier callback) {
        BwtConfig bwtConfig = new BwtConfig();
        bwtConfig.network = Network.get() == Network.MAINNET ? "bitcoin" : Network.get().getName();
        bwtConfig.descriptors = outputDescriptors;
        bwtConfig.rescanSince = rescanSince;
        bwtConfig.gapLimit = gapLimit;
        bwtConfig.verbose = log.isDebugEnabled() ? 2 : 0;
        bwtConfig.electrumAddr = "127.0.0.1:0";
        bwtConfig.electrumSkipMerkle = true;

        Config config = Config.get();
        bwtConfig.bitcoindUrl = config.getCoreServer();
        if(config.getCoreAuthType() == CoreAuthType.COOKIE) {
            bwtConfig.bitcoindDir = config.getCoreDataDir().getAbsolutePath() + "/";
        } else {
            bwtConfig.bitcoindAuth = config.getCoreAuth();
        }
        if(config.getCoreWallet() != null && !config.getCoreWallet().isEmpty()) {
            bwtConfig.bitcoindWallet = config.getCoreWallet();
        }

        Gson gson = new Gson();
        String jsonConfig = gson.toJson(bwtConfig);

        NativeBwtDaemon.start(jsonConfig, callback);
    }

    /**
     * Shut down the BWT daemon
     *
     * @param shutdownPtr the pointer provided on startup
     */
    private void shutdown(long shutdownPtr) {
        NativeBwtDaemon.shutdown(shutdownPtr);
    }

    public boolean isRunning() {
        return shutdownPtr != null;
    }

    public ConnectionService getConnectionService(Collection<Wallet> wallets) {
        return wallets != null ? new ConnectionService(wallets) : new ConnectionService();
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

        @SerializedName("descriptors")
        public List<String> descriptors;

        @SerializedName("xpubs")
        public String xpubs;

        @SerializedName("rescan_since")
        public Integer rescanSince;

        @SerializedName("gap_limit")
        public Integer gapLimit;

        @SerializedName("verbose")
        public Integer verbose;

        @SerializedName("electrum_addr")
        public String electrumAddr;

        @SerializedName("electrum_skip_merkle")
        public Boolean electrumSkipMerkle;
    }

    public final class ConnectionService extends Service<Void> {
        private final Collection<Wallet> wallets;

        public ConnectionService() {
            this.wallets = null;
        }

        public ConnectionService(Collection<Wallet> wallets) {
            this.wallets = wallets;
        }

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                protected Void call() {
                    CallbackNotifier notifier = new CallbackNotifier() {
                        @Override
                        public void onBooting() {
                            Platform.runLater(() -> EventManager.get().post(new BwtStatusEvent("Starting bwt")));
                        }

                        @Override
                        public void onSyncProgress(float progress, int tip) {
                            int percent = (int) (progress * 100.0);
                            Platform.runLater(() -> EventManager.get().post(new BwtSyncStatusEvent("Syncing (" + percent + "%)", percent, tip)));
                        }

                        @Override
                        public void onScanProgress(float progress, int eta) {
                            int percent = (int) (progress * 100.0);
                            Date date = new Date((long) eta * 1000);
                            Platform.runLater(() -> EventManager.get().post(new BwtScanStatusEvent("Scanning (" + percent + "%)", percent, date)));
                        }

                        @Override
                        public void onElectrumReady(String addr) {
                            Platform.runLater(() -> EventManager.get().post(new BwtElectrumReadyStatusEvent("Electrum server ready", addr)));
                        }

                        @Override
                        public void onHttpReady(String addr) {
                            log.info("http ready at " + addr);
                        }

                        @Override
                        public void onReady(long shutdownPtr) {
                            Bwt.this.shutdownPtr = shutdownPtr;
                            Platform.runLater(() -> EventManager.get().post(new BwtReadyStatusEvent("Server ready", shutdownPtr)));
                        }
                    };

                    if(wallets == null) {
                        Bwt.this.start(notifier);
                    } else {
                        Bwt.this.start(wallets, notifier);
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
                    if(shutdownPtr == null) {
                        throw new IllegalStateException("Bwt has not been started");
                    }

                    Bwt.this.shutdown(shutdownPtr);
                    shutdownPtr = null;
                    return null;
                }
            };
        }
    }
}
