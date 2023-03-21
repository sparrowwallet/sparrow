package com.sparrowwallet.sparrow.soroban;

import com.google.common.net.HostAndPort;
import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.soroban.client.SorobanServer;
import com.samourai.soroban.client.cahoots.SorobanCahootsService;
import com.samourai.soroban.client.rpc.RpcClient;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.cahoots.CahootsWallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.sparrowwallet.drongo.Drongo;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.nightjar.http.JavaHttpClientService;
import com.sparrowwallet.sparrow.AppServices;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Provider;
import java.util.*;

public class Soroban {
    private static final Logger log = LoggerFactory.getLogger(Soroban.class);

    protected static final HD_WalletFactoryGeneric hdWalletFactory = HD_WalletFactoryGeneric.getInstance();
    protected static final Bip47UtilJava bip47Util = Bip47UtilJava.getInstance();
    protected static final Provider PROVIDER_JAVA = Drongo.getProvider();
    protected static final int TIMEOUT_MS = 60000;
    public static final List<Network> SOROBAN_NETWORKS = List.of(Network.MAINNET, Network.TESTNET);

    private final SorobanServer sorobanServer;
    private final JavaHttpClientService httpClientService;

    private HD_Wallet hdWallet;
    private int bip47Account;

    public Soroban(Network network, HostAndPort torProxy) {
        this.sorobanServer = SorobanServer.valueOf(network.getName().toUpperCase(Locale.ROOT));
        this.httpClientService = new JavaHttpClientService(torProxy);
    }

    public HD_Wallet getHdWallet() {
        return hdWallet;
    }

    public void setHDWallet(Wallet wallet) {
        if(wallet.isEncrypted()) {
            throw new IllegalStateException("Wallet cannot be encrypted");
        }

        try {
            Keystore keystore = wallet.getKeystores().get(0);
            ScriptType scriptType = wallet.getScriptType();
            int purpose = scriptType.getDefaultDerivation().get(0).num();
            List<String> words = keystore.getSeed().getMnemonicCode();
            String passphrase = keystore.getSeed().getPassphrase() == null ? "" : keystore.getSeed().getPassphrase().asString();
            byte[] seed = hdWalletFactory.computeSeedFromWords(words);
            hdWallet = new HD_Wallet(purpose, new ArrayList<>(words), sorobanServer.getParams(), seed, passphrase);
            bip47Account = wallet.isMasterWallet() ? wallet.getAccountIndex() : wallet.getMasterWallet().getAccountIndex();
        } catch(Exception e) {
            throw new IllegalStateException("Could not create Soroban HD wallet ", e);
        }
    }

    public SparrowCahootsWallet getCahootsWallet(Wallet wallet, double feeRate) {
        if(wallet.getScriptType() != ScriptType.P2WPKH) {
            throw new IllegalArgumentException("Wallet must be P2WPKH");
        }

        if(hdWallet == null) {
            for(Wallet associatedWallet : wallet.getAllWallets()) {
                Soroban soroban = AppServices.getSorobanServices().getSoroban(associatedWallet);
                if(soroban != null && soroban.getHdWallet() != null) {
                    hdWallet = soroban.hdWallet;
                }
            }
        }

        if(hdWallet == null) {
            throw new IllegalStateException("HD wallet is not set");
        }

        try {
            return new SparrowCahootsWallet(wallet, hdWallet, bip47Account, sorobanServer, (long)feeRate);
        } catch(Exception e) {
            log.error("Could not create cahoots wallet", e);
        }

        return null;
    }

    public SorobanCahootsService getSorobanCahootsService(CahootsWallet cahootsWallet) {
        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        RpcClient rpcClient = new RpcClient(httpClient, httpClientService.getTorProxy() != null, sorobanServer.getParams());
        return new SorobanCahootsService(bip47Util, PROVIDER_JAVA, cahootsWallet, rpcClient);
    }

    public HostAndPort getTorProxy() {
        return httpClientService.getTorProxy();
    }

    public void setTorProxy(HostAndPort torProxy) {
        //Ensure all http clients are shutdown first
        httpClientService.shutdown();
        httpClientService.setTorProxy(torProxy);
    }

    public void shutdown() {
        httpClientService.shutdown();
    }

    public static class ShutdownService extends Service<Boolean> {
        private final Soroban soroban;

        public ShutdownService(Soroban soroban) {
            this.soroban = soroban;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws Exception {
                    soroban.shutdown();
                    return true;
                }
            };
        }
    }
}
