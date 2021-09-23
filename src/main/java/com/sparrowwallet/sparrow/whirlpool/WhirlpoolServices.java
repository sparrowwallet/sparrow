package com.sparrowwallet.sparrow.whirlpool;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.TorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

public class WhirlpoolServices {
    private static final Logger log = LoggerFactory.getLogger(WhirlpoolServices.class);

    private final Map<String, Whirlpool> whirlpoolMap = new HashMap<>();

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
            HostAndPort torProxy = getTorProxy();
            whirlpool = new Whirlpool(Network.get(), torProxy);
            whirlpoolMap.put(walletId, whirlpool);
        } else if(!whirlpool.isStarted()) {
            HostAndPort torProxy = getTorProxy();
            if(!Objects.equals(whirlpool.getTorProxy(), torProxy)) {
                whirlpool.setTorProxy(getTorProxy());
            }
        }

        return whirlpool;
    }

    private HostAndPort getTorProxy() {
        return AppServices.isTorRunning() ?
                HostAndPort.fromParts("localhost", TorService.PROXY_PORT) :
                (Config.get().getProxyServer() == null || Config.get().getProxyServer().isEmpty() || !Config.get().isUseProxy() ? null : HostAndPort.fromString(Config.get().getProxyServer()));
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
            HostAndPort torProxy = getTorProxy();
            if(!Objects.equals(whirlpool.getTorProxy(), torProxy)) {
                whirlpool.setTorProxy(getTorProxy());
            }

            try {
                String mixToWalletId = getWhirlpoolMixToWalletId(wallet.getMasterMixConfig());
                whirlpool.setMixToWallet(mixToWalletId, wallet.getMasterMixConfig().getMinMixes());
            } catch(NoSuchElementException e) {
                if(notifyIfMixToMissing) {
                    AppServices.showWarningDialog("Mix to wallet not open", wallet.getMasterName() + " is configured to mix to " + wallet.getMasterMixConfig().getMixToWalletName() + ", but this wallet is not open. Mix to wallets are required to be open to avoid address reuse.");
                }
            }

            Whirlpool.StartupService startupService = new Whirlpool.StartupService(whirlpool);
            startupService.setOnFailed(workerStateEvent -> {
                log.error("Failed to start whirlpool", workerStateEvent.getSource().getException());
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

    @Subscribe
    public void newConnection(ConnectionEvent event) {
        startAllWhirlpool();
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        stopAllWhirlpool();
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
                Whirlpool.RestartService restartService = new Whirlpool.RestartService(mixFromWhirlpool);
                restartService.setOnFailed(workerStateEvent -> {
                    log.error("Failed to restart whirlpool", workerStateEvent.getSource().getException());
                });
                restartService.start();
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
                    Whirlpool.RestartService restartService = new Whirlpool.RestartService(mixToWhirlpool);
                    restartService.setOnFailed(workerStateEvent -> {
                        log.error("Failed to restart whirlpool", workerStateEvent.getSource().getException());
                    });
                    restartService.start();
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
}
