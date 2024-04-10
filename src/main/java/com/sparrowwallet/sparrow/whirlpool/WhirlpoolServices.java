package com.sparrowwallet.sparrow.whirlpool;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.samourai.soroban.client.SorobanConfig;
import com.samourai.soroban.client.rpc.RpcClientService;
import com.samourai.wallet.constants.SamouraiNetwork;
import com.samourai.wallet.util.ExtLibJConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.sparrowwallet.drongo.Drongo;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.HttpClientService;
import com.sparrowwallet.sparrow.soroban.Soroban;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.getHttpClientService;
import static com.sparrowwallet.sparrow.AppServices.getTorProxy;
import static org.bitcoinj.crypto.MnemonicCode.SPARROW_FIX_NFKD_MNEMONIC;

public class WhirlpoolServices {
    private static final Logger log = LoggerFactory.getLogger(WhirlpoolServices.class);

    private final Map<String, Whirlpool> whirlpoolMap = new HashMap<>();

    private final SorobanConfig sorobanConfig;

    public WhirlpoolServices() {
        ExtLibJConfig extLibJConfig = computeExtLibJConfig();
        this.sorobanConfig = new SorobanConfig(extLibJConfig);
        System.setProperty(SPARROW_FIX_NFKD_MNEMONIC, "true");
    }

    private ExtLibJConfig computeExtLibJConfig() {
        HttpClientService httpClientService = AppServices.getHttpClientService();
        boolean onion = (getTorProxy() != null);
        SamouraiNetwork samouraiNetwork = getSamouraiNetwork();
        return new ExtLibJConfig(samouraiNetwork, onion, Drongo.getProvider(), httpClientService);
    }

    public SamouraiNetwork getSamouraiNetwork() {
        return SamouraiNetwork.valueOf(Network.get().getName().toUpperCase(Locale.ROOT));
    }

    public Whirlpool getWhirlpool(Wallet wallet) {
        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        for(Map.Entry<Wallet, Storage> entry : AppServices.get().getOpenWallets().entrySet()) {
            if(entry.getKey() == masterWallet) {
                return whirlpoolMap.get(entry.getValue().getWalletId(entry.getKey()));
            }
        }

        return null;
    }

    public Whirlpool getWhirlpool(String walletId) {
        Whirlpool whirlpool = whirlpoolMap.get(walletId);
        if(whirlpool == null) {
            Wallet wallet = AppServices.get().getWallet(walletId);
            whirlpool = new Whirlpool(wallet == null ? null : wallet.getStoredBlockHeight());
            whirlpoolMap.put(walletId, whirlpool);
        }

        return whirlpool;
    }

    private void bindDebugAccelerator() {
        List<Window> windows = whirlpoolMap.keySet().stream().map(walletId -> AppServices.get().getWindowForWallet(walletId)).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for(Window window : windows) {
            KeyCombination keyCombination = new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN);
            if(window.getScene() != null && !window.getScene().getAccelerators().containsKey(keyCombination)) {
                window.getScene().getAccelerators().put(keyCombination, () -> {
                    for(Whirlpool whirlpool : whirlpoolMap.values()) {
                        whirlpool.logDebug();
                    }
                });
            }
        }
    }

    private void startAllWhirlpool() {
        for(Map.Entry<String, Whirlpool> entry : whirlpoolMap.entrySet().stream().filter(entry -> entry.getValue().hasWallet() && !entry.getValue().isStarted()).collect(Collectors.toList())) {
            Wallet wallet = AppServices.get().getWallet(entry.getKey());
            Whirlpool whirlpool = entry.getValue();
            startWhirlpool(wallet, whirlpool, false);
        }
    }

    public void startWhirlpool(Wallet wallet, Whirlpool whirlpool, boolean notifyIfMixToMissing) {
        if(wallet.getMasterMixConfig().getMixOnStartup() != Boolean.FALSE) {
            try {
                String mixToWalletId = getWhirlpoolMixToWalletId(wallet.getMasterMixConfig());
                whirlpool.setMixToWallet(mixToWalletId, wallet.getMasterMixConfig().getMinMixes());
            } catch(NoSuchElementException e) {
                if(notifyIfMixToMissing) {
                    AppServices.showWarningDialog("Mix to wallet not open", wallet.getMasterName() + " is configured to mix to " + wallet.getMasterMixConfig().getMixToWalletName() + ", but this wallet is not open. Mix to wallets are required to be open to avoid address reuse.");
                }
            }

            if(wallet.getMasterMixConfig() != null) {
                whirlpool.setPostmixIndexRange(wallet.getMasterMixConfig().getIndexRange());
            }

            Whirlpool.StartupService startupService = whirlpool.createStartupService();
            if(whirlpool.getStartupServiceDelay() != null) {
                startupService.setDelay(whirlpool.getStartupServiceDelay());
                whirlpool.setStartupServiceDelay(null);
            }

            startupService.setPeriod(Duration.minutes(2));
            startupService.setOnSucceeded(workerStateEvent -> {
                startupService.cancel();
            });
            startupService.setOnFailed(workerStateEvent -> {
                Throwable exception = workerStateEvent.getSource().getException();
                while(exception.getCause() != null) {
                    exception = exception.getCause();
                }
                if(exception instanceof TimeoutException || exception instanceof SocketTimeoutException) {
                    EventManager.get().post(new StatusEvent("Error connecting to Whirlpool server, will retry soon..."));
                    HostAndPort torProxy = getTorProxy();
                    if(torProxy != null) {
                        whirlpool.refreshTorCircuits();
                    }
                    log.error("Error connecting to Whirlpool server: " + exception.getMessage());
                } else {
                    log.error("Failed to start Whirlpool", workerStateEvent.getSource().getException());
                }
            });
            startupService.start();
        }
    }

    private void stopAllWhirlpool() {
        for(Whirlpool whirlpool : whirlpoolMap.values().stream().filter(Whirlpool::isStarted).collect(Collectors.toList())) {
            stopWhirlpool(whirlpool, false);
        }
    }

    public void stopWhirlpool(Whirlpool whirlpool, boolean notifyOnFailure) {
        Whirlpool.ShutdownService shutdownService = new Whirlpool.ShutdownService(whirlpool);
        shutdownService.setOnFailed(workerStateEvent -> {
            log.error("Failed to stop whirlpool", workerStateEvent.getSource().getException());
            if(notifyOnFailure) {
                AppServices.showErrorDialog("Failed to stop whirlpool", workerStateEvent.getSource().getException().getMessage());
            }
        });
        shutdownService.start();
    }

    public String getWhirlpoolMixToWalletId(MixConfig mixConfig) {
        if(mixConfig == null || mixConfig.getMixToWalletFile() == null || mixConfig.getMixToWalletName() == null) {
            return null;
        }

        return AppServices.get().getOpenWallets().entrySet().stream()
                .filter(entry -> entry.getValue().getWalletFile().equals(mixConfig.getMixToWalletFile()) && entry.getKey().getName().equals(mixConfig.getMixToWalletName()))
                .map(entry -> entry.getValue().getWalletId(entry.getKey()))
                .findFirst().orElseThrow();
    }

    public Whirlpool getWhirlpoolForMixToWallet(String walletId) {
        return whirlpoolMap.values().stream().filter(whirlpool -> walletId.equals(whirlpool.getMixToWalletId())).findFirst().orElse(null);
    }

    public static boolean canWalletMix(Wallet wallet) {
        return Whirlpool.WHIRLPOOL_NETWORKS.contains(Network.get())
                && wallet.getScriptType() != ScriptType.P2TR    //Taproot not yet supported
                && wallet.getKeystores().size() == 1
                && wallet.getKeystores().get(0).hasSeed()
                && wallet.getKeystores().get(0).getSeed().getType() == DeterministicSeed.Type.BIP39
                && wallet.getStandardAccountType() != null
                && StandardAccount.isMixableAccount(wallet.getStandardAccountType());
    }

    public static boolean canWatchPostmix(Wallet wallet) {
        return Whirlpool.WHIRLPOOL_NETWORKS.contains(Network.get())
                && wallet.getScriptType() != ScriptType.P2TR    //Taproot not yet supported
                && wallet.getKeystores().size() == 1;
    }

    public static List<Wallet> prepareWhirlpoolWallet(Wallet decryptedWallet, String walletId, Storage storage) {
        Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(walletId);
        whirlpool.setScode(decryptedWallet.getMasterMixConfig().getScode());
        whirlpool.setHDWallet(walletId, decryptedWallet);
        whirlpool.setResyncMixesDone(true);

        Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
        soroban.setHDWallet(decryptedWallet);

        List<Wallet> childWallets = new ArrayList<>();
        for(StandardAccount whirlpoolAccount : StandardAccount.WHIRLPOOL_ACCOUNTS) {
            if(decryptedWallet.getChildWallet(whirlpoolAccount) == null) {
                Wallet childWallet = decryptedWallet.addChildWallet(whirlpoolAccount);
                childWallets.add(childWallet);
                EventManager.get().post(new ChildWalletsAddedEvent(storage, decryptedWallet, childWallet));
            }
        }

        return childWallets;
    }

    @Subscribe
    public void newConnection(ConnectionEvent event) {
        ExtLibJConfig extLibJConfig = sorobanConfig.getExtLibJConfig();
        extLibJConfig.setOnion(getTorProxy() != null);
        getHttpClientService(); //Ensure proxy is updated

        startAllWhirlpool();
        bindDebugAccelerator();
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        //Cancel any scheduled attempts to try reconnect
        whirlpoolMap.values().stream().filter(whirlpool -> whirlpool.getStartupService() != null).forEach(whirlpool -> whirlpool.getStartupService().cancel());
        stopAllWhirlpool();
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        for(Whirlpool whirlpool : whirlpoolMap.values()) {
            whirlpool.checkIfMixing();
        }
    }

    @Subscribe
    public void walletOpened(WalletOpenedEvent event) {
        String walletId = event.getStorage().getWalletId(event.getWallet());
        Whirlpool whirlpool = whirlpoolMap.get(walletId);
        if(whirlpool != null && !whirlpool.isStarted() && AppServices.isConnected()) {
            startWhirlpool(event.getWallet(), whirlpool, true);
        }

        Whirlpool mixFromWhirlpool = whirlpoolMap.entrySet().stream()
                .filter(entry -> {
                    MixConfig mixConfig = AppServices.get().getWallet(entry.getKey()).getMasterMixConfig();
                    return event.getStorage().getWalletFile().equals(mixConfig.getMixToWalletFile()) && event.getWallet().getName().equals(mixConfig.getMixToWalletName());
                })
                .map(Map.Entry::getValue).findFirst().orElse(null);

        if(mixFromWhirlpool != null) {
            mixFromWhirlpool.setMixToWallet(walletId, AppServices.get().getWallet(mixFromWhirlpool.getWalletId()).getMasterMixConfig().getMinMixes());
            if(mixFromWhirlpool.isStarted()) {
                //Will automatically restart
                stopWhirlpool(mixFromWhirlpool, false);
            }
        }
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        for(WalletTabData walletTabData : event.getClosedWalletTabData()) {
            String walletId = walletTabData.getStorage().getWalletId(walletTabData.getWallet());
            Whirlpool whirlpool = whirlpoolMap.remove(walletId);
            if(whirlpool != null) {
                if(whirlpool.isStarted()) {
                    Whirlpool.ShutdownService shutdownService = new Whirlpool.ShutdownService(whirlpool);
                    shutdownService.setOnSucceeded(workerStateEvent -> {
                        WhirlpoolEventService.getInstance().unregister(whirlpool);
                    });
                    shutdownService.setOnFailed(workerStateEvent -> {
                        log.error("Failed to shutdown whirlpool", workerStateEvent.getSource().getException());
                    });
                    shutdownService.start();
                } else {
                    //Ensure http clients are shutdown
                    whirlpool.shutdown();
                    WhirlpoolEventService.getInstance().unregister(whirlpool);
                }
            }

            Whirlpool mixToWhirlpool = getWhirlpoolForMixToWallet(walletId);
            if(mixToWhirlpool != null && event.getClosedWalletTabData().stream().noneMatch(walletTabData1 -> walletTabData1.getWalletForm().getWalletId().equals(mixToWhirlpool.getWalletId()))) {
                mixToWhirlpool.setMixToWallet(null, null);
                if(mixToWhirlpool.isStarted()) {
                    //Will automatically restart
                    stopWhirlpool(mixToWhirlpool, false);
                }
            }
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        Whirlpool whirlpool = getWhirlpool(event.getWallet());
        if(whirlpool != null) {
            whirlpool.refreshUtxos();
        }
    }

    @Subscribe
    public void whirlpoolIndexHighFrequency(WhirlpoolIndexHighFrequencyEvent event) {
        Whirlpool whirlpool = getWhirlpool(event.getWallet());
        if(whirlpool != null && whirlpool.isStarted() && !whirlpool.isStopping()) {
            log.warn("Rapidly increasing address index detected, temporarily disconnecting " + event.getWallet().getMasterName() + " from Whirlpool");
            Platform.runLater(() -> {
                EventManager.get().post(new StatusEvent("Error communicating with Whirlpool, will retry soon..."));
                whirlpool.setStartupServiceDelay(Duration.minutes(5));
                stopWhirlpool(whirlpool, false);
            });
        }
    }

    public SorobanConfig getSorobanConfig() {
        return sorobanConfig;
    }
}
