package com.sparrowwallet.sparrow.whirlpool;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.samourai.tor.client.TorClientService;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.whirlpool.client.event.MixFailEvent;
import com.samourai.whirlpool.client.event.MixSuccessEvent;
import com.samourai.whirlpool.client.event.WalletStartEvent;
import com.samourai.whirlpool.client.event.WalletStopEvent;
import com.samourai.whirlpool.client.tx0.*;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.data.dataPersister.DataPersisterFactory;
import com.samourai.whirlpool.client.wallet.data.dataPersister.FileDataPersister;
import com.samourai.whirlpool.client.wallet.data.dataSource.DataSourceFactory;
import com.samourai.whirlpool.client.wallet.data.pool.PoolData;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoSupplier;
import com.samourai.whirlpool.client.whirlpool.ServerApi;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.ExtendedKey;
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
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Whirlpool {
    private static final Logger log = LoggerFactory.getLogger(Whirlpool.class);

    private final HostAndPort torProxy;
    private final WhirlpoolServer whirlpoolServer;
    private final JavaHttpClientService httpClientService;
    private final JavaStompClientService stompClientService;
    private final TorClientService torClientService;
    private final WhirlpoolWalletService whirlpoolWalletService;
    private final WhirlpoolWalletConfig config;
    private HD_Wallet hdWallet;

    public Whirlpool(Network network, HostAndPort torProxy, String sCode, int maxClients) {
        this.torProxy = torProxy;
        this.whirlpoolServer = WhirlpoolServer.valueOf(network.getName().toUpperCase());
        this.httpClientService = new JavaHttpClientService(torProxy);
        this.stompClientService = new JavaStompClientService(httpClientService);
        this.torClientService = new WhirlpoolTorClientService();

        DataPersisterFactory dataPersisterFactory = (config, bip44w, walletIdentifier) -> new FileDataPersister(config, bip44w, walletIdentifier);
        DataSourceFactory dataSourceFactory = (config, bip44w, walletIdentifier, dataPersister) -> new SparrowDataSource(config, bip44w, walletIdentifier, dataPersister);
        this.whirlpoolWalletService = new WhirlpoolWalletService(dataPersisterFactory, dataSourceFactory);
        this.config = computeWhirlpoolWalletConfig(sCode, maxClients);

        WhirlpoolEventService.getInstance().register(this);
    }

    private WhirlpoolWalletConfig computeWhirlpoolWalletConfig(String sCode, int maxClients) {
        boolean onion = (torProxy != null);
        String serverUrl = whirlpoolServer.getServerUrl(onion);
        ServerApi serverApi = new ServerApi(serverUrl, httpClientService);

        WhirlpoolWalletConfig whirlpoolWalletConfig = new WhirlpoolWalletConfig(httpClientService, stompClientService, torClientService, serverApi, whirlpoolServer.getParams(), false);
        whirlpoolWalletConfig.setScode(sCode);

        return whirlpoolWalletConfig;
    }

    public Collection<Pool> getPools() throws Exception {
        Tx0ParamService tx0ParamService = getTx0ParamService();
        PoolData poolData = new PoolData(config.getServerApi().fetchPools(), tx0ParamService);
        return poolData.getPools();
    }

    public Tx0Preview getTx0Preview(Pool pool, Collection<UnspentOutput> utxos) throws Exception {
        Tx0Config tx0Config = new Tx0Config();
        tx0Config.setChangeWallet(WhirlpoolAccount.BADBANK);
        Tx0FeeTarget tx0FeeTarget = Tx0FeeTarget.BLOCKS_4;
        Tx0FeeTarget mixFeeTarget = Tx0FeeTarget.BLOCKS_4;

        Tx0ParamService tx0ParamService = getTx0ParamService();

        Tx0Service tx0Service = new Tx0Service(config);
        return tx0Service.tx0Preview(utxos, tx0Config, tx0ParamService.getTx0Param(pool, tx0FeeTarget, mixFeeTarget));
    }

    public Tx0 broadcastTx0(Pool pool, Collection<BlockTransactionHashIndex> utxos) throws Exception {
        WhirlpoolWallet whirlpoolWallet = getWhirlpoolWallet();
        whirlpoolWallet.start();
        UtxoSupplier utxoSupplier = whirlpoolWallet.getUtxoSupplier();
        List<WhirlpoolUtxo> whirlpoolUtxos = utxos.stream().map(ref -> utxoSupplier.findUtxo(ref.getHashAsString(), (int)ref.getIndex())).filter(Objects::nonNull).collect(Collectors.toList());

        if(whirlpoolUtxos.size() != utxos.size()) {
            throw new IllegalStateException("Failed to find UTXOs in Whirlpool wallet");
        }

        Tx0Config tx0Config = new Tx0Config();
        tx0Config.setChangeWallet(WhirlpoolAccount.BADBANK);
        Tx0FeeTarget tx0FeeTarget = Tx0FeeTarget.BLOCKS_4;
        Tx0FeeTarget mixFeeTarget = Tx0FeeTarget.BLOCKS_4;

        return whirlpoolWallet.tx0(whirlpoolUtxos, pool, tx0Config, tx0FeeTarget, mixFeeTarget);
    }

    private Tx0ParamService getTx0ParamService() {
        try {
            SparrowMinerFeeSupplier minerFeeSupplier = SparrowMinerFeeSupplier.getInstance();
            return new Tx0ParamService(minerFeeSupplier, config);
        } catch(Exception e) {
            log.error("Error fetching miner fees", e);
        }

        return null;
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
            HD_WalletFactoryGeneric hdWalletFactory = HD_WalletFactoryGeneric.getInstance();
            byte[] seed = hdWalletFactory.computeSeedFromWords(words);
            hdWallet = new HD_Wallet(purpose, words, config.getNetworkParameters(), seed, passphrase, 1);
        } catch(Exception e) {
            throw new IllegalStateException("Could not create Whirlpool HD wallet ", e);
        }
    }

    public WhirlpoolWallet getWhirlpoolWallet() throws WhirlpoolException {
        if(whirlpoolWalletService.getWhirlpoolWalletOrNull() != null) {
            return whirlpoolWalletService.getWhirlpoolWalletOrNull();
        }

        if(hdWallet == null) {
            throw new IllegalStateException("Whirlpool HD wallet not added");
        }

        try {
            return whirlpoolWalletService.openWallet(config, Utils.hexToBytes(hdWallet.getSeedHex()), hdWallet.getPassphrase());
        } catch(Exception e) {
            throw new WhirlpoolException("Could not create whirlpool wallet ", e);
        }
    }

    public HostAndPort getTorProxy() {
        return torProxy;
    }

    public boolean hasWallet() {
        return hdWallet != null;
    }

    public boolean isStarted() {
        if(whirlpoolWalletService.getWhirlpoolWalletOrNull() == null) {
            return false;
        }

        return whirlpoolWalletService.getWhirlpoolWalletOrNull().isStarted();
    }

    public void shutdown() {
        whirlpoolWalletService.closeWallet();
        httpClientService.shutdown();
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

    public String getScode() {
        return config.getScode();
    }

    public void setScode(String scode) {
        config.setScode(scode);
    }

    @Subscribe
    public void onMixFail(MixFailEvent e) {
        log.info("Mix failed for utxo " + e.getMixFail().getWhirlpoolUtxo().getUtxo().tx_hash + ":" + e.getMixFail().getWhirlpoolUtxo().getUtxo().tx_output_n);
    }

    @Subscribe
    public void onMixSuccess(MixSuccessEvent e) {
        log.info("Mix success, new utxo " + e.getMixSuccess().getReceiveUtxo().getHash() + ":" + e.getMixSuccess().getReceiveUtxo().getIndex());
    }

    @Subscribe
    public void onWalletStart(WalletStartEvent e) {
        log.info("Wallet started");
    }

    @Subscribe
    public void onWalletStop(WalletStopEvent e) {
        log.info("Wallet stopped");
    }

    public static class PoolsService extends Service<Collection<Pool>> {
        private final Whirlpool whirlpool;

        public PoolsService(Whirlpool whirlpool) {
            this.whirlpool = whirlpool;
        }

        @Override
        protected Task<Collection<Pool>> createTask() {
            return new Task<>() {
                protected Collection<Pool> call() throws Exception {
                    return whirlpool.getPools();
                }
            };
        }
    }

    public static class Tx0PreviewService extends Service<Tx0Preview> {
        private final Whirlpool whirlpool;
        private final Wallet wallet;
        private final Pool pool;
        private final List<UtxoEntry> utxoEntries;

        public Tx0PreviewService(Whirlpool whirlpool, Wallet wallet, Pool pool, List<UtxoEntry> utxoEntries) {
            this.whirlpool = whirlpool;
            this.wallet = wallet;
            this.pool = pool;
            this.utxoEntries = utxoEntries;
        }

        @Override
        protected Task<Tx0Preview> createTask() {
            return new Task<>() {
                protected Tx0Preview call() throws Exception {
                    updateProgress(-1, 1);
                    updateMessage("Fetching premix transaction...");

                    Collection<UnspentOutput> utxos = utxoEntries.stream().map(utxoEntry -> Whirlpool.getUnspentOutput(wallet, utxoEntry.getNode(), utxoEntry.getBlockTransaction(), (int)utxoEntry.getHashIndex().getIndex())).collect(Collectors.toList());
                    return whirlpool.getTx0Preview(pool, utxos);
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

    public static class StartupService extends Service<WhirlpoolWallet> {
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

                    WhirlpoolWallet whirlpoolWallet = whirlpool.getWhirlpoolWallet();
                    if(AppServices.onlineProperty().get()) {
                        whirlpoolWallet.start();
                    }

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

                    whirlpool.shutdown();
                    return true;
                }
            };
        }
    }
}
