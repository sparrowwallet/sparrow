package com.sparrowwallet.sparrow.whirlpool;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.samourai.tor.client.TorClientService;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.whirlpool.client.event.*;
import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.tx0.*;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.wallet.data.dataPersister.DataPersisterFactory;
import com.samourai.whirlpool.client.wallet.data.dataSource.DataSourceFactory;
import com.samourai.whirlpool.client.wallet.data.pool.ExpirablePoolSupplier;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoSupplier;
import com.samourai.whirlpool.client.wallet.data.utxoConfig.UtxoConfig;
import com.samourai.whirlpool.client.whirlpool.ServerApi;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.nightjar.http.JavaHttpClientService;
import com.sparrowwallet.nightjar.stomp.JavaStompClientService;
import com.sparrowwallet.nightjar.tor.WhirlpoolTorClientService;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WhirlpoolMixEvent;
import com.sparrowwallet.sparrow.event.WhirlpoolMixSuccessEvent;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.whirlpool.dataPersister.SparrowDataPersister;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowDataSource;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowMinerFeeSupplier;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowPostmixHandler;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Whirlpool {
    private static final Logger log = LoggerFactory.getLogger(Whirlpool.class);

    public static final List<Network> WHIRLPOOL_NETWORKS = List.of(Network.MAINNET, Network.TESTNET);
    public static final int DEFAULT_MIXTO_MIN_MIXES = 5;
    public static final int DEFAULT_MIXTO_RANDOM_FACTOR = 4;

    private final WhirlpoolServer whirlpoolServer;
    private final JavaHttpClientService httpClientService;
    private final JavaStompClientService stompClientService;
    private final TorClientService torClientService;
    private final WhirlpoolWalletService whirlpoolWalletService;
    private final WhirlpoolWalletConfig config;
    private Tx0ParamService tx0ParamService;
    private ExpirablePoolSupplier poolSupplier;
    private Tx0Service tx0Service;
    private HD_Wallet hdWallet;
    private String walletId;
    private String mixToWalletId;

    private StartupService startupService;

    private final BooleanProperty startingProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty stoppingProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty mixingProperty = new SimpleBooleanProperty(false);

    public Whirlpool(Network network, HostAndPort torProxy) {
        this.whirlpoolServer = WhirlpoolServer.valueOf(network.getName().toUpperCase());
        this.httpClientService = new JavaHttpClientService(torProxy);
        this.stompClientService = new JavaStompClientService(httpClientService);
        this.torClientService = new WhirlpoolTorClientService();

        this.whirlpoolWalletService = new WhirlpoolWalletService();
        this.config = computeWhirlpoolWalletConfig(torProxy);
        this.tx0ParamService = new Tx0ParamService(SparrowMinerFeeSupplier.getInstance(), config);
        this.poolSupplier = new ExpirablePoolSupplier(config.getRefreshPoolsDelay(), config.getServerApi(), tx0ParamService);
        this.tx0Service = new Tx0Service(config);

        WhirlpoolEventService.getInstance().register(this);
    }

    private WhirlpoolWalletConfig computeWhirlpoolWalletConfig(HostAndPort torProxy) {
        DataPersisterFactory dataPersisterFactory = (whirlpoolWallet, bip44w) -> new SparrowDataPersister(whirlpoolWallet);
        DataSourceFactory dataSourceFactory = (whirlpoolWallet, bip44w, dataPersister) -> new SparrowDataSource(whirlpoolWallet, bip44w, dataPersister);

        boolean onion = (torProxy != null);
        String serverUrl = whirlpoolServer.getServerUrl(onion);
        ServerApi serverApi = new ServerApi(serverUrl, httpClientService);

        WhirlpoolWalletConfig whirlpoolWalletConfig = new WhirlpoolWalletConfig(dataSourceFactory, httpClientService, stompClientService, torClientService, serverApi, whirlpoolServer.getParams(), false);
        whirlpoolWalletConfig.setDataPersisterFactory(dataPersisterFactory);
        whirlpoolWalletConfig.setPartner("SPARROW");
        return whirlpoolWalletConfig;
    }

    public Collection<Pool> getPools(Long totalUtxoValue) throws Exception {
        this.poolSupplier.load();
        if(totalUtxoValue == null) {
            return poolSupplier.getPools();
        }

        return tx0ParamService.findPools(poolSupplier.getPools(), totalUtxoValue);
    }

    public Tx0Previews getTx0Previews(Collection<UnspentOutput> utxos) throws Exception {
        // preview all pools
        Tx0Config tx0Config = computeTx0Config();
        return tx0Service.tx0Previews(utxos, tx0Config);
    }

    public Tx0 broadcastTx0(Pool pool, Collection<BlockTransactionHashIndex> utxos) throws Exception {
        WhirlpoolWallet whirlpoolWallet = getWhirlpoolWallet();
        whirlpoolWallet.start();
        UtxoSupplier utxoSupplier = whirlpoolWallet.getUtxoSupplier();
        List<WhirlpoolUtxo> whirlpoolUtxos = utxos.stream().map(ref -> utxoSupplier.findUtxo(ref.getHashAsString(), (int)ref.getIndex())).filter(Objects::nonNull).collect(Collectors.toList());

        if(whirlpoolUtxos.size() != utxos.size()) {
            throw new IllegalStateException("Failed to find UTXOs in Whirlpool wallet");
        }

        Tx0Config tx0Config = computeTx0Config();
        return whirlpoolWallet.tx0(whirlpoolUtxos, pool, tx0Config);
    }

    private Tx0Config computeTx0Config() {
        Tx0FeeTarget tx0FeeTarget = Tx0FeeTarget.BLOCKS_4;
        Tx0FeeTarget mixFeeTarget = Tx0FeeTarget.BLOCKS_4;
        return new Tx0Config(tx0ParamService, poolSupplier, tx0FeeTarget, mixFeeTarget, WhirlpoolAccount.BADBANK);
    }

    public void setHDWallet(String walletId, Wallet wallet) {
        if(wallet.isEncrypted()) {
            throw new IllegalStateException("Wallet cannot be encrypted");
        }

        try {
            Keystore keystore = wallet.getKeystores().get(0);
            ScriptType scriptType = wallet.getScriptType();
            int purpose = scriptType.getDefaultDerivation().get(0).num();
            List<String> words = keystore.getSeed().getMnemonicCode();
            String passphrase = keystore.getSeed().getPassphrase().asString();
            HD_WalletFactoryGeneric hdWalletFactory = HD_WalletFactoryGeneric.getInstance();
            byte[] seed = hdWalletFactory.computeSeedFromWords(words);
            this.walletId = walletId;
            hdWallet = new HD_Wallet(purpose, words, config.getNetworkParameters(), seed, passphrase, 1);
        } catch(Exception e) {
            throw new IllegalStateException("Could not create Whirlpool HD wallet ", e);
        }
    }

    public WhirlpoolWallet getWhirlpoolWallet() throws WhirlpoolException {
        if(whirlpoolWalletService.whirlpoolWallet() != null) {
            return whirlpoolWalletService.whirlpoolWallet();
        }

        if(hdWallet == null) {
            throw new IllegalStateException("Whirlpool HD wallet not added");
        }

        try {
            WhirlpoolWallet whirlpoolWallet = new WhirlpoolWallet(config, Utils.hexToBytes(hdWallet.getSeedHex()), hdWallet.getPassphrase(), walletId);
            return whirlpoolWalletService.openWallet(whirlpoolWallet);
        } catch(Exception e) {
            throw new WhirlpoolException("Could not create whirlpool wallet ", e);
        }
    }

    public void stop() {
        if(whirlpoolWalletService.whirlpoolWallet() != null) {
            whirlpoolWalletService.whirlpoolWallet().stop();
        }
    }

    public UtxoMixData getMixData(BlockTransactionHashIndex txo) {
        if(whirlpoolWalletService.whirlpoolWallet() != null) {
            WhirlpoolUtxo whirlpoolUtxo = whirlpoolWalletService.whirlpoolWallet().getUtxoSupplier().findUtxo(txo.getHashAsString(), (int)txo.getIndex());
            if (whirlpoolUtxo != null) {
                UtxoConfig utxoConfig = whirlpoolUtxo.getUtxoConfigOrDefault();
                return new UtxoMixData(utxoConfig.getMixsDone(), null);
            }
        }

        return null;
    }

    public void mix(BlockTransactionHashIndex utxo) throws WhirlpoolException {
        if(whirlpoolWalletService.whirlpoolWallet() == null) {
            throw new WhirlpoolException("Whirlpool wallet not yet created");
        }

        try {
            WhirlpoolUtxo whirlpoolUtxo = whirlpoolWalletService.whirlpoolWallet().getUtxoSupplier().findUtxo(utxo.getHashAsString(), (int)utxo.getIndex());
            whirlpoolWalletService.whirlpoolWallet().mix(whirlpoolUtxo);
        } catch(Exception e) {
            throw new WhirlpoolException(e.getMessage(), e);
        }
    }

    public void mixStop(BlockTransactionHashIndex utxo) throws WhirlpoolException {
        if(whirlpoolWalletService.whirlpoolWallet() == null) {
            throw new WhirlpoolException("Whirlpool wallet not yet created");
        }

        try {
            WhirlpoolUtxo whirlpoolUtxo = whirlpoolWalletService.whirlpoolWallet().getUtxoSupplier().findUtxo(utxo.getHashAsString(), (int)utxo.getIndex());
            whirlpoolWalletService.whirlpoolWallet().mixStop(whirlpoolUtxo);
        } catch(Exception e) {
            throw new WhirlpoolException(e.getMessage(), e);
        }
    }

    public MixProgress getMixProgress(BlockTransactionHashIndex utxo) {
        if(whirlpoolWalletService.whirlpoolWallet() == null) {
            return null;
        }

        WhirlpoolUtxo whirlpoolUtxo = whirlpoolWalletService.whirlpoolWallet().getUtxoSupplier().findUtxo(utxo.getHashAsString(), (int)utxo.getIndex());
        if(whirlpoolUtxo != null && whirlpoolUtxo.getUtxoState() != null) {
            return whirlpoolUtxo.getUtxoState().getMixProgress();
        }

        return null;
    }

    public void refreshUtxos() {
        if(whirlpoolWalletService.whirlpoolWallet() != null) {
            whirlpoolWalletService.whirlpoolWallet().refreshUtxos();
        }
    }

    public boolean hasWallet() {
        return hdWallet != null;
    }

    public boolean isStarted() {
        if(whirlpoolWalletService.whirlpoolWallet() == null) {
            return false;
        }

        return whirlpoolWalletService.whirlpoolWallet().isStarted();
    }

    public void shutdown() {
        whirlpoolWalletService.closeWallet();
        httpClientService.shutdown();
    }

    public StartupService createStartupService() {
        if(startupService != null) {
            startupService.cancel();
        }

        startupService = new StartupService(this);
        return startupService;
    }

    private WalletUtxo getUtxo(WhirlpoolUtxo whirlpoolUtxo) {
        Wallet wallet = AppServices.get().getWallet(walletId);
        if(wallet != null) {
            wallet = getStandardAccountWallet(whirlpoolUtxo.getAccount(), wallet);

            if(wallet != null) {
                for(BlockTransactionHashIndex utxo : wallet.getWalletUtxos().keySet()) {
                    if(utxo.getHashAsString().equals(whirlpoolUtxo.getUtxo().tx_hash) && utxo.getIndex() == whirlpoolUtxo.getUtxo().tx_output_n) {
                        return new WalletUtxo(wallet, utxo);
                    }
                }
            }
        }

        return null;
    }

    public static Wallet getWallet(String walletId) {
        return AppServices.get().getOpenWallets().entrySet().stream().filter(entry -> entry.getValue().getWalletId(entry.getKey()).equals(walletId)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public static Wallet getStandardAccountWallet(WhirlpoolAccount whirlpoolAccount, Wallet wallet) {
        StandardAccount standardAccount = getStandardAccount(whirlpoolAccount);
        if(StandardAccount.WHIRLPOOL_ACCOUNTS.contains(standardAccount)) {
            wallet = wallet.getChildWallet(standardAccount);
        }
        return wallet;
    }

    public static StandardAccount getStandardAccount(WhirlpoolAccount whirlpoolAccount) {
        if(whirlpoolAccount == WhirlpoolAccount.PREMIX) {
            return StandardAccount.WHIRLPOOL_PREMIX;
        } else if(whirlpoolAccount == WhirlpoolAccount.POSTMIX) {
            return StandardAccount.WHIRLPOOL_POSTMIX;
        } else if(whirlpoolAccount == WhirlpoolAccount.BADBANK) {
            return StandardAccount.WHIRLPOOL_BADBANK;
        }

        return StandardAccount.ACCOUNT_0;
    }

    public static UnspentOutput getUnspentOutput(Wallet wallet, WalletNode node, BlockTransaction blockTransaction, int index) {
        TransactionOutput txOutput = blockTransaction.getTransaction().getOutputs().get(index);

        UnspentOutput out = new UnspentOutput();
        out.tx_hash = txOutput.getHash().toString();
        out.tx_output_n = txOutput.getIndex();
        out.value = txOutput.getValue();
        out.script = Utils.bytesToHex(txOutput.getScriptBytes());

        try {
            out.addr = txOutput.getScript().getToAddresses()[0].toString();
        } catch(Exception e) {
            //ignore
        }

        Transaction transaction = (Transaction)txOutput.getParent();
        out.tx_version = (int)transaction.getVersion();
        out.tx_locktime = transaction.getLocktime();
        if(AppServices.getCurrentBlockHeight() != null) {
            out.confirmations = blockTransaction.getConfirmations(AppServices.getCurrentBlockHeight());
        }

        if(wallet.getKeystores().size() != 1) {
            throw new IllegalStateException("Cannot mix outputs from a wallet with multiple keystores");
        }

        UnspentOutput.Xpub xpub = new UnspentOutput.Xpub();
        List<ExtendedKey.Header> headers = ExtendedKey.Header.getHeaders(Network.get());
        ExtendedKey.Header header = headers.stream().filter(head -> head.getDefaultScriptType().equals(wallet.getScriptType()) && !head.isPrivateKey()).findFirst().orElse(ExtendedKey.Header.xpub);
        xpub.m = wallet.getKeystores().get(0).getExtendedPublicKey().toString(header);
        xpub.path = node.getDerivationPath().toUpperCase();

        out.xpub = xpub;

        return out;
    }

    public HostAndPort getTorProxy() {
        return httpClientService.getTorProxy();
    }

    public void setTorProxy(HostAndPort torProxy) {
        if(isStarted()) {
            throw new IllegalStateException("Cannot set tor proxy on a started Whirlpool");
        }

        //Ensure all http clients are shutdown first
        httpClientService.shutdown();

        httpClientService.setTorProxy(torProxy);
        String serverUrl = whirlpoolServer.getServerUrl(torProxy != null);
        ServerApi serverApi = new ServerApi(serverUrl, httpClientService);
        config.setServerApi(serverApi);
    }

    public String getScode() {
        return config.getScode();
    }

    public void setScode(String scode) {
        config.setScode(scode);
    }

    public String getWalletId() {
        return walletId;
    }

    public String getMixToWalletId() {
        return mixToWalletId;
    }

    public void setMixToWallet(String mixToWalletId, Integer minMixes) {
        if(mixToWalletId == null) {
            config.setExternalDestination(null);
        } else {
            Wallet mixToWallet = getWallet(mixToWalletId);
            if(mixToWallet == null) {
                throw new IllegalStateException("Cannot find mix to wallet with id " + mixToWalletId);
            }

            Integer highestUsedIndex = mixToWallet.getNode(KeyPurpose.RECEIVE).getHighestUsedIndex();
            int startIndex = highestUsedIndex == null ? 0 : highestUsedIndex + 1;
            int mixes = minMixes == null ? DEFAULT_MIXTO_MIN_MIXES : minMixes;

            IPostmixHandler postmixHandler = new SparrowPostmixHandler(whirlpoolWalletService, mixToWallet, KeyPurpose.RECEIVE, startIndex);
            ExternalDestination externalDestination = new ExternalDestination(postmixHandler, 0, startIndex, mixes, DEFAULT_MIXTO_RANDOM_FACTOR);
            config.setExternalDestination(externalDestination);
        }

        this.mixToWalletId = mixToWalletId;
    }

    public boolean isMixing() {
        return mixingProperty.get();
    }

    public BooleanProperty mixingProperty() {
        return mixingProperty;
    }

    public boolean isStarting() {
        return startingProperty.get();
    }

    public BooleanProperty startingProperty() {
        return startingProperty;
    }

    public boolean isStopping() {
        return stoppingProperty.get();
    }

    public BooleanProperty stoppingProperty() {
        return stoppingProperty;
    }

    @Subscribe
    public void onMixSuccess(MixSuccessEvent e) {
        WalletUtxo walletUtxo = getUtxo(e.getWhirlpoolUtxo());
        if(walletUtxo != null) {
            log.debug("Mix success, new utxo " + e.getReceiveUtxo().getHash() + ":" + e.getReceiveUtxo().getIndex());
            Platform.runLater(() -> EventManager.get().post(new WhirlpoolMixSuccessEvent(walletUtxo.wallet, walletUtxo.utxo, e.getReceiveUtxo(), getReceiveNode(e, walletUtxo))));
        }
    }

    private WalletNode getReceiveNode(MixSuccessEvent e, WalletUtxo walletUtxo) {
        for(WalletNode walletNode : walletUtxo.wallet.getNode(KeyPurpose.RECEIVE).getChildren()) {
            if(walletUtxo.wallet.getAddress(walletNode).toString().equals(e.getMixProgress().getDestination().getAddress())) {
                return walletNode;
            }
        }

        return null;
    }

    @Subscribe
    public void onMixFail(MixFailEvent e) {
        WalletUtxo walletUtxo = getUtxo(e.getWhirlpoolUtxo());
        if(walletUtxo != null) {
            log.debug("Mix failed for utxo " + e.getWhirlpoolUtxo().getUtxo().tx_hash + ":" + e.getWhirlpoolUtxo().getUtxo().tx_output_n + " " + e.getMixFailReason());
            Platform.runLater(() -> EventManager.get().post(new WhirlpoolMixEvent(walletUtxo.wallet, walletUtxo.utxo, e.getMixFailReason(), e.getError())));
        }
    }

    @Subscribe
    public void onMixProgress(MixProgressEvent e) {
        WalletUtxo walletUtxo = getUtxo(e.getWhirlpoolUtxo());
        if(walletUtxo != null && isMixing()) {
            log.debug("Mix progress for utxo " + e.getWhirlpoolUtxo().getUtxo().tx_hash + ":" + e.getWhirlpoolUtxo().getUtxo().tx_output_n + " " + e.getWhirlpoolUtxo().getMixsDone() + " " + e.getMixProgress().getMixStep() + " " + e.getWhirlpoolUtxo().getUtxoState().getStatus());
            Platform.runLater(() -> EventManager.get().post(new WhirlpoolMixEvent(walletUtxo.wallet, walletUtxo.utxo, e.getMixProgress())));
        }
    }

    @Subscribe
    public void onWalletStart(WalletStartEvent e) {
        if(e.getWhirlpoolWallet() == whirlpoolWalletService.whirlpoolWallet()) {
            log.info("Mixing to " + e.getWhirlpoolWallet().getConfig().getExternalDestination());
            mixingProperty.set(true);
        }
    }

    @Subscribe
    public void onWalletStop(WalletStopEvent e) {
        if(e.getWhirlpoolWallet() == whirlpoolWalletService.whirlpoolWallet()) {
            mixingProperty.set(false);

            Wallet wallet = AppServices.get().getWallet(walletId);
            if(wallet != null) {
                Platform.runLater(() -> {
                    if(AppServices.isConnected()) {
                        AppServices.getWhirlpoolServices().startWhirlpool(wallet, this, false);
                    }
                });
            }
        }
    }

    public static class PoolsService extends Service<Collection<Pool>> {
        private final Whirlpool whirlpool;
        private final Long totalUtxoValue;

        public PoolsService(Whirlpool whirlpool, Long totalUtxoValue) {
            this.whirlpool = whirlpool;
            this.totalUtxoValue = totalUtxoValue;
        }

        @Override
        protected Task<Collection<Pool>> createTask() {
            return new Task<>() {
                protected Collection<Pool> call() throws Exception {
                    return whirlpool.getPools(totalUtxoValue);
                }
            };
        }
    }

    public static class Tx0PreviewsService extends Service<Tx0Previews> {
        private final Whirlpool whirlpool;
        private final Wallet wallet;
        private final List<UtxoEntry> utxoEntries;

        public Tx0PreviewsService(Whirlpool whirlpool, Wallet wallet, List<UtxoEntry> utxoEntries) {
            this.whirlpool = whirlpool;
            this.wallet = wallet;
            this.utxoEntries = utxoEntries;
        }

        @Override
        protected Task<Tx0Previews> createTask() {
            return new Task<>() {
                protected Tx0Previews call() throws Exception {
                    updateProgress(-1, 1);
                    updateMessage("Fetching premix preview...");

                    Collection<UnspentOutput> utxos = utxoEntries.stream().map(utxoEntry -> Whirlpool.getUnspentOutput(wallet, utxoEntry.getNode(), utxoEntry.getBlockTransaction(), (int)utxoEntry.getHashIndex().getIndex())).collect(Collectors.toList());
                    return whirlpool.getTx0Previews(utxos);
                }
            };
        }
    }

    public static class Tx0BroadcastService extends Service<Sha256Hash> {
        private final Whirlpool whirlpool;
        private final Pool pool;
        private final Collection<BlockTransactionHashIndex> utxos;

        public Tx0BroadcastService(Whirlpool whirlpool, Pool pool, Collection<BlockTransactionHashIndex> utxos) {
            this.whirlpool = whirlpool;
            this.pool = pool;
            this.utxos = utxos;
        }

        @Override
        protected Task<Sha256Hash> createTask() {
            return new Task<>() {
                protected Sha256Hash call() throws Exception {
                    updateProgress(-1, 1);
                    updateMessage("Broadcasting premix transaction...");

                    Tx0 tx0 = whirlpool.broadcastTx0(pool, utxos);
                    return Sha256Hash.wrap(tx0.getTxid());
                }
            };
        }
    }

    public static class StartupService extends ScheduledService<WhirlpoolWallet> {
        private final Whirlpool whirlpool;

        public StartupService(Whirlpool whirlpool) {
            this.whirlpool = whirlpool;
        }

        @Override
        protected Task<WhirlpoolWallet> createTask() {
            return new Task<>() {
                protected WhirlpoolWallet call() throws Exception {
                    updateProgress(-1, 1);
                    updateMessage("Starting Whirlpool...");

                    whirlpool.startingProperty.set(true);
                    WhirlpoolWallet whirlpoolWallet = whirlpool.getWhirlpoolWallet();
                    if(AppServices.onlineProperty().get()) {
                        whirlpoolWallet.start();
                    }
                    whirlpool.startingProperty.set(false);

                    return whirlpoolWallet;
                }
            };
        }
    }

    public static class ShutdownService extends Service<Boolean> {
        private final Whirlpool whirlpool;

        public ShutdownService(Whirlpool whirlpool) {
            this.whirlpool = whirlpool;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws Exception {
                    updateProgress(-1, 1);
                    updateMessage("Disconnecting from Whirlpool...");
                    whirlpool.stoppingProperty.set(true);
                    whirlpool.shutdown();
                    whirlpool.stoppingProperty.set(false);
                    return true;
                }
            };
        }
    }

    public static class WalletUtxo {
        public final Wallet wallet;
        public final BlockTransactionHashIndex utxo;

        public WalletUtxo(Wallet wallet, BlockTransactionHashIndex utxo) {
            this.wallet = wallet;
            this.utxo = utxo;
        }
    }
}
