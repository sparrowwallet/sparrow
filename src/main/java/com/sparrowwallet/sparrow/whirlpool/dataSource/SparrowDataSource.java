package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.google.common.eventbus.Subscribe;
import com.samourai.wallet.api.backend.IPushTx;
import com.samourai.wallet.api.backend.ISweepBackend;
import com.samourai.wallet.api.backend.seenBackend.ISeenBackend;
import com.samourai.wallet.api.backend.seenBackend.SeenBackendWithFallback;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.httpClient.HttpUsage;
import com.samourai.wallet.httpClient.IHttpClient;
import com.samourai.wallet.util.ExtLibJConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.data.coordinator.CoordinatorSupplier;
import com.samourai.whirlpool.client.wallet.data.dataSource.AbstractDataSource;
import com.samourai.whirlpool.client.wallet.data.dataSource.DataSourceConfig;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoSupplier;
import com.samourai.whirlpool.client.wallet.data.utxoConfig.UtxoConfigSupplier;
import com.samourai.whirlpool.client.wallet.data.walletState.WalletStateSupplier;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletAddressesChangedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import javafx.application.Platform;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class SparrowDataSource extends AbstractDataSource {
    private static final Logger log = LoggerFactory.getLogger(SparrowDataSource.class);

    private final ISeenBackend seenBackend;
    private final IPushTx pushTx;
    private final SparrowUtxoSupplier utxoSupplier;

    public SparrowDataSource(
            WhirlpoolWallet whirlpoolWallet,
            HD_Wallet bip44w,
            WalletStateSupplier walletStateSupplier,
            UtxoConfigSupplier utxoConfigSupplier,
            DataSourceConfig dataSourceConfig)
            throws Exception {
        super(whirlpoolWallet, bip44w, walletStateSupplier, dataSourceConfig);
        this.seenBackend = computeSeenBackend(whirlpoolWallet.getConfig());
        this.pushTx = computePushTx();
        NetworkParameters params = whirlpoolWallet.getConfig().getSamouraiNetwork().getParams();
        this.utxoSupplier = new SparrowUtxoSupplier(walletSupplier, utxoConfigSupplier, dataSourceConfig, params);
    }

    private ISeenBackend computeSeenBackend(WhirlpoolWalletConfig whirlpoolWalletConfig) {
        ExtLibJConfig extLibJConfig = whirlpoolWalletConfig.getSorobanConfig().getExtLibJConfig();
        IHttpClient httpClient = extLibJConfig.getHttpClientService().getHttpClient(HttpUsage.BACKEND);
        ISeenBackend sparrowSeenBackend = new SparrowSeenBackend(getWhirlpoolWallet().getWalletIdentifier(), httpClient);
        NetworkParameters params = whirlpoolWalletConfig.getSamouraiNetwork().getParams();
        return SeenBackendWithFallback.withOxt(sparrowSeenBackend, params);
    }

    private IPushTx computePushTx() {
        return new IPushTx() {
            @Override
            public String pushTx(String hexTx) throws Exception {
                Transaction transaction = new Transaction(Utils.hexToBytes(hexTx));
                ElectrumServer electrumServer = new ElectrumServer();
                return electrumServer.broadcastTransactionPrivately(transaction).toString();
            }

            @Override
            public String pushTx(String txHex, Collection<Integer> strictModeVouts) throws Exception {
                return pushTx(txHex);
            }
        };
    }

    @Override
    public void open(CoordinatorSupplier coordinatorSupplier) throws Exception {
        super.open(coordinatorSupplier);
        EventManager.get().register(this);
        ((SparrowChainSupplier)getDataSourceConfig().getChainSupplier()).open();
    }

    @Override
    protected void load(boolean initial) throws Exception {
        super.load(initial);
        utxoSupplier.refresh();
    }

    @Override
    public void close() throws Exception {
        EventManager.get().unregister(this);
        ((SparrowChainSupplier)getDataSourceConfig().getChainSupplier()).close();
    }

    @Override
    public IPushTx getPushTx() {
        return pushTx;
    }

    public static Wallet getWallet(String zpub) {
        return AppServices.get().getOpenWallets().keySet().stream()
                .filter(wallet -> {
                    try {
                        List<ExtendedKey.Header> headers = ExtendedKey.Header.getHeaders(Network.get());
                        ExtendedKey.Header header = headers.stream().filter(head -> head.getDefaultScriptType().equals(wallet.getScriptType()) && !head.isPrivateKey()).findFirst().orElse(ExtendedKey.Header.xpub);
                        ExtendedKey extPubKey = wallet.getKeystores().get(0).getExtendedPublicKey();
                        return extPubKey.toString(header).equals(zpub);
                    } catch(Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        refreshWallet(event.getWalletId(), event.getWallet(), 0);
    }

    @Subscribe
    public void walletAddressesChanged(WalletAddressesChangedEvent event) {
        refreshWallet(event.getWalletId(), event.getWallet(), 0);
    }

    private void refreshWallet(String walletId, Wallet wallet, int i) {
        try {
            // prefix matching <prefix>:master, :Premix, :Postmix
            String walletIdentifierPrefix = getWhirlpoolWallet().getWalletIdentifier().replace(":master", "");
            // match <prefix>:master, :Premix, :Postmix
            if(walletId.startsWith(walletIdentifierPrefix) && (wallet.isWhirlpoolMasterWallet() || wallet.isWhirlpoolChildWallet())) {
                //Workaround to avoid refreshing the wallet after it has been opened, but before it has been started
                Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(wallet);
                if(whirlpool != null && whirlpool.isStarting() && i < 1000) {
                    Platform.runLater(() -> refreshWallet(walletId, wallet, i+1));
                } else {
                    utxoSupplier.refresh();
                }
            }
        } catch (Exception e) {
            log.error("Error refreshing wallet", e);
        }
    }

    @Override
    public ISweepBackend getSweepBackend() {
        return null; // not necessary
    }

    @Override
    public ISeenBackend getSeenBackend() {
        return seenBackend;
    }

    @Override
    public UtxoSupplier getUtxoSupplier() {
        return utxoSupplier;
    }
}
