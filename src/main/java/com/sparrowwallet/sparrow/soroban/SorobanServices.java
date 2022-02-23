package com.sparrowwallet.sparrow.soroban;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.WalletTabsClosedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.TorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.sparrowwallet.sparrow.AppServices.getTorProxy;

public class SorobanServices {
    private static final Logger log = LoggerFactory.getLogger(SorobanServices.class);

    private final Map<String, Soroban> sorobanMap = new HashMap<>();

    public Soroban getSoroban(Wallet wallet) {
        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        for(Map.Entry<Wallet, Storage> entry : AppServices.get().getOpenWallets().entrySet()) {
            if(entry.getKey() == masterWallet) {
                return sorobanMap.get(entry.getValue().getWalletId(entry.getKey()));
            }
        }

        return null;
    }

    public Soroban getSoroban(String walletId) {
        Soroban soroban = sorobanMap.get(walletId);
        if(soroban == null) {
            HostAndPort torProxy = getTorProxy();
            soroban = new Soroban(Network.get(), torProxy);
            sorobanMap.put(walletId, soroban);
        } else {
            HostAndPort torProxy = getTorProxy();
            if(!Objects.equals(soroban.getTorProxy(), torProxy)) {
                soroban.setTorProxy(getTorProxy());
            }
        }

        return soroban;
    }

    public static boolean canWalletMix(Wallet wallet) {
        return Soroban.SOROBAN_NETWORKS.contains(Network.get())
                && wallet.getKeystores().size() == 1
                && wallet.getKeystores().get(0).hasSeed()
                && wallet.getKeystores().get(0).getSeed().getType() == DeterministicSeed.Type.BIP39
                && wallet.getScriptType() == ScriptType.P2WPKH;
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        for(WalletTabData walletTabData : event.getClosedWalletTabData()) {
            String walletId = walletTabData.getStorage().getWalletId(walletTabData.getWallet());
            Soroban soroban = sorobanMap.remove(walletId);
            if(soroban != null) {
                Soroban.ShutdownService shutdownService = new Soroban.ShutdownService(soroban);
                shutdownService.setOnFailed(failedEvent -> {
                    log.error("Failed to shutdown soroban", failedEvent.getSource().getException());
                });
                shutdownService.start();
            }
        }
    }
}