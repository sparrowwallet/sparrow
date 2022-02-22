package com.sparrowwallet.sparrow.soroban;

import com.google.common.net.HostAndPort;
import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.soroban.client.SorobanServer;
import com.samourai.soroban.client.cahoots.SorobanCahootsService;
import com.samourai.soroban.client.rpc.RpcClient;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.cahoots.CahootsWallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.sparrowwallet.drongo.Drongo;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.DumpedPrivateKey;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.nightjar.http.JavaHttpClientService;
import com.sparrowwallet.sparrow.AppServices;
import io.reactivex.Observable;
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
    private final PayNymService payNymService;

    private HD_Wallet hdWallet;
    private BIP47Wallet bip47Wallet;
    private PaymentCode paymentCode;

    public Soroban(Network network, HostAndPort torProxy) {
        this.sorobanServer = SorobanServer.valueOf(network.getName().toUpperCase());
        this.httpClientService = new JavaHttpClientService(torProxy);
        this.payNymService = new PayNymService(httpClientService);
    }

    public HD_Wallet getHdWallet() {
        return hdWallet;
    }

    public PaymentCode getPaymentCode() {
        return paymentCode;
    }

    public void setPaymentCode(Wallet wallet) {
        if(wallet.isEncrypted()) {
            throw new IllegalStateException("Wallet cannot be encrypted");
        }

        try {
            Keystore keystore = wallet.getKeystores().get(0);
            List<String> words = keystore.getSeed().getMnemonicCode();
            String passphrase = keystore.getSeed().getPassphrase().asString();
            byte[] seed = hdWalletFactory.computeSeedFromWords(words);
            BIP47Wallet bip47Wallet = hdWalletFactory.getBIP47(Utils.bytesToHex(seed), passphrase, sorobanServer.getParams());
            paymentCode = bip47Util.getPaymentCode(bip47Wallet, wallet.isMasterWallet() ? wallet.getAccountIndex() : wallet.getMasterWallet().getAccountIndex());
        } catch(Exception e) {
            throw new IllegalStateException("Could not create payment code", e);
        }
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
            String passphrase = keystore.getSeed().getPassphrase().asString();
            byte[] seed = hdWalletFactory.computeSeedFromWords(words);
            hdWallet = new HD_Wallet(purpose, new ArrayList<>(words), sorobanServer.getParams(), seed, passphrase);
            bip47Wallet = hdWalletFactory.getBIP47(hdWallet.getSeedHex(), hdWallet.getPassphrase(), sorobanServer.getParams());
            paymentCode = bip47Util.getPaymentCode(bip47Wallet, wallet.isMasterWallet() ? wallet.getAccountIndex() : wallet.getMasterWallet().getAccountIndex());
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
                    bip47Wallet = soroban.bip47Wallet;
                    paymentCode = soroban.paymentCode;
                }
            }
        }

        if(hdWallet == null) {
            throw new IllegalStateException("HD wallet is not set");
        }

        try {
            return new SparrowCahootsWallet(wallet, hdWallet, sorobanServer, (long)feeRate);
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

    public Observable<Map<String, Object>> createPayNym() {
        return payNymService.createPayNym(paymentCode);
    }

    public Observable<Map<String, Object>> updateToken() {
        return payNymService.updateToken(paymentCode);
    }

    public Observable<Map<String, Object>> claimPayNym(String authToken, String signature) {
        return payNymService.claimPayNym(authToken, signature);
    }

    public Observable<Map<String, Object>> addPaymentCode(String authToken, String signature, boolean segwit) {
        return payNymService.addPaymentCode(paymentCode, authToken, signature, segwit);
    }

    public Observable<Map<String, Object>> followPaymentCode(PaymentCode paymentCode, String authToken, String signature) {
        return payNymService.followPaymentCode(paymentCode, authToken, signature);
    }

    public Observable<PayNym> getPayNym(String nymIdentifier) {
        return payNymService.getPayNym(nymIdentifier);
    }

    public Observable<List<PayNym>> getFollowing() {
        return payNymService.getPayNym(paymentCode.toString()).map(PayNym::following);
    }

    public Observable<String> getAuthToken(Map<String, Object> map) {
        if(map.containsKey("token")) {
            return Observable.just((String)map.get("token"));
        }

        return updateToken().map(tokenMap -> (String)tokenMap.get("token"));
    }

    public String getSignature(String authToken) {
        ECKey notificationAddressKey = DumpedPrivateKey.fromBase58(bip47Wallet.getAccount(0).addressAt(0).getPrivateKeyString()).getKey();
        return notificationAddressKey.signMessage(authToken, ScriptType.P2PKH);
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
