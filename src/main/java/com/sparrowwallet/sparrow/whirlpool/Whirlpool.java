package com.sparrowwallet.sparrow.whirlpool;

import com.google.common.eventbus.Subscribe;
import com.samourai.soroban.client.SorobanConfig;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.bipFormat.BIP_FORMAT;
import com.samourai.wallet.bipWallet.WalletSupplier;
import com.samourai.wallet.constants.BIP_WALLETS;
import com.samourai.wallet.constants.SamouraiAccount;
import com.samourai.wallet.constants.SamouraiNetwork;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactoryGeneric;
import com.samourai.wallet.util.AsyncUtil;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.client.event.*;
import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Config;
import com.samourai.whirlpool.client.tx0.Tx0Info;
import com.samourai.whirlpool.client.tx0.Tx0Previews;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.wallet.data.WhirlpoolInfo;
import com.samourai.whirlpool.client.wallet.data.coordinator.CoordinatorSupplier;
import com.samourai.whirlpool.client.wallet.data.dataPersister.DataPersisterFactory;
import com.samourai.whirlpool.client.wallet.data.dataSource.DataSourceConfig;
import com.samourai.whirlpool.client.wallet.data.dataSource.DataSourceFactory;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoSupplier;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WhirlpoolMixEvent;
import com.sparrowwallet.sparrow.event.WhirlpoolMixSuccessEvent;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.whirlpool.dataPersister.SparrowDataPersister;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowChainSupplier;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowDataSource;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowMinerFeeSupplier;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowPostmixHandler;
import io.reactivex.Single;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.util.Duration;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class Whirlpool {
    private static final Logger log = LoggerFactory.getLogger(Whirlpool.class);

    public static final List<Network> WHIRLPOOL_NETWORKS = List.of(Network.MAINNET, Network.TESTNET);
    public static final int DEFAULT_MIXTO_MIN_MIXES = 3;
    public static final int DEFAULT_MIXTO_RANDOM_FACTOR = 4;

    private final WhirlpoolWalletService whirlpoolWalletService;
    private final WhirlpoolWalletConfig config;
    private WhirlpoolInfo whirlpoolInfo;
    private Tx0Info tx0Info;
    private Tx0FeeTarget tx0FeeTarget = Tx0FeeTarget.BLOCKS_4;
    private Tx0FeeTarget mixFeeTarget = Tx0FeeTarget.BLOCKS_4;
    private HD_Wallet hdWallet;
    private String walletId;
    private String mixToWalletId;
    private boolean resyncMixesDone;

    private StartupService startupService;
    private Duration startupServiceDelay;

    private final BooleanProperty startingProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty stoppingProperty = new SimpleBooleanProperty(false);
    private final BooleanProperty mixingProperty = new SimpleBooleanProperty(false);

    public Whirlpool(Integer storedBlockHeight) {
        this.whirlpoolWalletService = new WhirlpoolWalletService();
        this.config = computeWhirlpoolWalletConfig(storedBlockHeight);
        this.tx0Info = null; // instantiated by getTx0Info()
        this.whirlpoolInfo = null; // instantiated by getWhirlpoolInfo()

        WhirlpoolEventService.getInstance().register(this);
    }

    private WhirlpoolWalletConfig computeWhirlpoolWalletConfig(Integer storedBlockHeight) {
        SorobanConfig sorobanConfig = AppServices.getWhirlpoolServices().getSorobanConfig();
        DataSourceConfig dataSourceConfig = computeDataSourceConfig(storedBlockHeight);
        DataSourceFactory dataSourceFactory = (whirlpoolWallet, bip44w, passphrase, walletStateSupplier, utxoConfigSupplier) -> new SparrowDataSource(whirlpoolWallet, bip44w, walletStateSupplier, utxoConfigSupplier, dataSourceConfig);

        WhirlpoolWalletConfig whirlpoolWalletConfig = new WhirlpoolWalletConfig(dataSourceFactory, sorobanConfig, false);
        DataPersisterFactory dataPersisterFactory = (whirlpoolWallet, bip44w) -> new SparrowDataPersister(whirlpoolWallet, whirlpoolWalletConfig.getPersistDelaySeconds());
        whirlpoolWalletConfig.setDataPersisterFactory(dataPersisterFactory);
        whirlpoolWalletConfig.setPartner("SPARROW");
        whirlpoolWalletConfig.setIndexRangePostmix(IndexRange.FULL);
        return whirlpoolWalletConfig;
    }

    private DataSourceConfig computeDataSourceConfig(Integer storedBlockHeight) {
        return new DataSourceConfig(SparrowMinerFeeSupplier.getInstance(), new SparrowChainSupplier(storedBlockHeight), BIP_FORMAT.PROVIDER, BIP_WALLETS.WHIRLPOOL);
    }

    private WhirlpoolInfo getWhirlpoolInfo() {
        if(whirlpoolInfo == null) {
            whirlpoolInfo = new WhirlpoolInfo(SparrowMinerFeeSupplier.getInstance(), config);
        }

        return whirlpoolInfo;
    }

    public Collection<Pool> getPools(Long totalUtxoValue) throws Exception {
        CoordinatorSupplier coordinatorSupplier = getWhirlpoolInfo().getCoordinatorSupplier();
        coordinatorSupplier.load();
        if(totalUtxoValue == null) {
            return coordinatorSupplier.getPools();
        }

        return coordinatorSupplier.findPoolsForTx0(totalUtxoValue);
    }

    public Tx0Previews getTx0Previews(Collection<UnspentOutput> utxos) throws Exception {
        Tx0Info tx0Info = getTx0Info();

        // preview all pools
        Tx0Config tx0Config = computeTx0Config(tx0Info);
        return tx0Info.tx0Previews(tx0Config, utxos);
    }

    public Tx0 broadcastTx0(Pool pool, Collection<BlockTransactionHashIndex> utxos) throws Exception {
        WhirlpoolWallet whirlpoolWallet = getWhirlpoolWallet();
        whirlpoolWallet.startAsync().subscribeOn(Schedulers.io()).observeOn(JavaFxScheduler.platform());
        UtxoSupplier utxoSupplier = whirlpoolWallet.getUtxoSupplier();
        List<WhirlpoolUtxo> whirlpoolUtxos = utxos.stream().map(ref -> utxoSupplier.findUtxo(ref.getHashAsString(), (int)ref.getIndex())).filter(Objects::nonNull).collect(Collectors.toList());

        if(whirlpoolUtxos.size() != utxos.size()) {
            throw new IllegalStateException("Failed to find UTXOs in Whirlpool wallet");
        }

        Tx0Info tx0Info = getTx0Info();

        WalletSupplier walletSupplier = whirlpoolWallet.getWalletSupplier();
        Tx0Config tx0Config = computeTx0Config(tx0Info);
        Tx0 tx0 = tx0Info.tx0(walletSupplier, utxoSupplier, whirlpoolUtxos, pool, tx0Config);

        //Clear tx0 for new fee addresses
        clearTx0Info();
        return tx0;
    }

    private Tx0Info getTx0Info() throws Exception {
        if(tx0Info == null) {
            tx0Info = fetchTx0Info();
        }

        return tx0Info;
    }

    private Tx0Info fetchTx0Info() throws Exception {
        return AsyncUtil.getInstance().blockingGet(
                Single.fromCallable(() -> getWhirlpoolInfo().fetchTx0Info(getScode()))
                        .subscribeOn(Schedulers.io()).observeOn(JavaFxScheduler.platform()));
    }

    private void clearTx0Info() {
        tx0Info = null;
    }

    private Tx0Config computeTx0Config(Tx0Info tx0Info) {
        Tx0Config tx0Config = tx0Info.getTx0Config(tx0FeeTarget, mixFeeTarget);
        tx0Config.setChangeWallet(SamouraiAccount.BADBANK);
        return tx0Config;
    }

    public void setHDWallet(String walletId, Wallet wallet) {
        NetworkParameters params = config.getSamouraiNetwork().getParams();
        this.hdWallet = computeHdWallet(wallet, params);
        this.walletId = walletId;
    }

    public static HD_Wallet computeHdWallet(Wallet wallet, NetworkParameters params) {
        if(wallet.isEncrypted()) {
            throw new IllegalStateException("Wallet cannot be encrypted");
        }

        try {
            Keystore keystore = wallet.getKeystores().get(0);
            ScriptType scriptType = wallet.getScriptType();
            int purpose = scriptType.getDefaultDerivation().get(0).num();
            List<String> words = keystore.getSeed().getMnemonicCode();
            String passphrase = keystore.getSeed().getPassphrase() == null ? "" : keystore.getSeed().getPassphrase().asString();
            HD_WalletFactoryGeneric hdWalletFactory = HD_WalletFactoryGeneric.getInstance();
            byte[] seed = hdWalletFactory.computeSeedFromWords(words);
            return hdWalletFactory.getHD(purpose, seed, passphrase, params);
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
            return whirlpoolWalletService.openWallet(whirlpoolWallet, hdWallet.getPassphrase());
        } catch(Exception e) {
            throw new WhirlpoolException("Could not create whirlpool wallet ", e);
        }
    }

    public void stop() {
        if(whirlpoolWalletService.whirlpoolWallet() != null) {
            whirlpoolWalletService.whirlpoolWallet().stop();
        }
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
        if(whirlpoolWalletService.whirlpoolWallet() == null || utxo.getStatus() == Status.FROZEN) {
            return null;
        }

        WhirlpoolUtxo whirlpoolUtxo = whirlpoolWalletService.whirlpoolWallet().getUtxoSupplier().findUtxo(utxo.getHashAsString(), (int)utxo.getIndex());
        if(whirlpoolUtxo != null && whirlpoolUtxo.getUtxoState() != null) {
            MixProgress mixProgress = whirlpoolUtxo.getUtxoState().getMixProgress();
            if(mixProgress != null && !isMixing(utxo)) {
                log.debug("Utxo " + utxo + " mix state is " + whirlpoolUtxo.getUtxoState() + " but utxo is not mixing");
                return null;
            }

            return mixProgress;
        }

        return null;
    }

    private boolean isMixing(BlockTransactionHashIndex utxo) {
        if(whirlpoolWalletService.whirlpoolWallet() == null || !whirlpoolWalletService.whirlpoolWallet().isStarted()) {
            return false;
        }

        return whirlpoolWalletService.whirlpoolWallet().getMixingState().getUtxosMixing().stream().map(WhirlpoolUtxo::getUtxo).anyMatch(uo -> uo.tx_hash.equals(utxo.getHashAsString()) && uo.tx_output_n == (int)utxo.getIndex());
    }

    public void refreshUtxos() {
        if(whirlpoolWalletService.whirlpoolWallet() != null) {
            whirlpoolWalletService.whirlpoolWallet().refreshUtxosAsync().subscribeOn(Schedulers.io()).observeOn(JavaFxScheduler.platform());
        }
    }

    private void resyncMixesDone(Whirlpool whirlpool, Wallet postmixWallet) {
        Set<BlockTransactionHashIndex> receiveUtxos = postmixWallet.getWalletUtxos().entrySet().stream()
                .filter(entry -> entry.getValue().getKeyPurpose() == KeyPurpose.RECEIVE).map(Map.Entry::getKey).collect(Collectors.toSet());
        for(BlockTransactionHashIndex utxo : receiveUtxos) {
            int mixesDone = recountMixesDone(postmixWallet, utxo);
            whirlpool.setMixesDone(utxo, mixesDone);
        }
    }

    public int recountMixesDone(Wallet postmixWallet, BlockTransactionHashIndex postmixUtxo) {
        int mixesDone = 0;
        Set<BlockTransactionHashIndex> walletTxos = postmixWallet.getWalletTxos().entrySet().stream()
                .filter(entry -> entry.getValue().getKeyPurpose() == KeyPurpose.RECEIVE).map(Map.Entry::getKey).collect(Collectors.toSet());
        BlockTransaction blkTx = postmixWallet.getTransactions().get(postmixUtxo.getHash());

        while(blkTx != null) {
            mixesDone++;
            List<TransactionInput> inputs = blkTx.getTransaction().getInputs();
            blkTx = null;
            for(TransactionInput txInput : inputs) {
                BlockTransaction inputTx = postmixWallet.getTransactions().get(txInput.getOutpoint().getHash());
                if(inputTx != null && walletTxos.stream().anyMatch(txo -> txo.getHash().equals(inputTx.getHash()) && txo.getIndex() == txInput.getOutpoint().getIndex()) && inputTx.getTransaction() != null) {
                    blkTx = inputTx;
                }
            }
        }

        return mixesDone;
    }

    public void setMixesDone(BlockTransactionHashIndex utxo, int mixesDone) {
        if(whirlpoolWalletService.whirlpoolWallet() == null) {
            return;
        }

        WhirlpoolUtxo whirlpoolUtxo = whirlpoolWalletService.whirlpoolWallet().getUtxoSupplier().findUtxo(utxo.getHashAsString(), (int)utxo.getIndex());
        if(whirlpoolUtxo != null) {
            whirlpoolUtxo.setMixsDone(mixesDone);
        }
    }

    public void checkIfMixing() {
        if(whirlpoolWalletService.whirlpoolWallet() == null) {
            return;
        }

        if(isMixing()) {
            if(!whirlpoolWalletService.whirlpoolWallet().isStarted()) {
                log.warn("Wallet is not started, but mixingProperty is true");
                WhirlpoolEventService.getInstance().post(new WalletStopEvent(whirlpoolWalletService.whirlpoolWallet()));
            } else if(whirlpoolWalletService.whirlpoolWallet().getMixingState().getUtxosMixing().isEmpty() &&
                    !whirlpoolWalletService.whirlpoolWallet().getUtxoSupplier().findUtxos(SamouraiAccount.PREMIX, SamouraiAccount.POSTMIX).isEmpty()) {
                log.warn("No UTXOs mixing, but mixingProperty is true");
                //Will automatically restart
                AppServices.getWhirlpoolServices().stopWhirlpool(this, false);
            }
        }
    }

    public void logDebug() {
        if(whirlpoolWalletService.whirlpoolWallet() == null) {
            log.warn("Whirlpool wallet for " + walletId + " not started");
            return;
        }

        log.warn("Whirlpool debug for " + walletId + "\n" + whirlpoolWalletService.whirlpoolWallet().getDebug());
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
    }

    public StartupService createStartupService() {
        if(startupService != null) {
            startupService.cancel();
        }

        startupService = new StartupService(this);
        return startupService;
    }

    public StartupService getStartupService() {
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

    public static Wallet getStandardAccountWallet(SamouraiAccount whirlpoolAccount, Wallet wallet) {
        StandardAccount standardAccount = getStandardAccount(whirlpoolAccount);
        if(StandardAccount.isWhirlpoolAccount(standardAccount) || wallet.getStandardAccountType() != standardAccount) {
            Wallet standardWallet = wallet.getChildWallet(standardAccount);
            if(standardWallet == null) {
                throw new IllegalStateException("Cannot find " + standardAccount + " wallet");
            }

            return standardWallet;
        }

        return wallet;
    }

    public static StandardAccount getStandardAccount(SamouraiAccount whirlpoolAccount) {
        if(whirlpoolAccount == SamouraiAccount.PREMIX) {
            return StandardAccount.WHIRLPOOL_PREMIX;
        } else if(whirlpoolAccount == SamouraiAccount.POSTMIX) {
            return StandardAccount.WHIRLPOOL_POSTMIX;
        } else if(whirlpoolAccount == SamouraiAccount.BADBANK) {
            return StandardAccount.WHIRLPOOL_BADBANK;
        }

        return StandardAccount.ACCOUNT_0;
    }

    public static UnspentOutput getUnspentOutput(WalletNode node, BlockTransaction blockTransaction, int index) {
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

        Wallet wallet = node.getWallet().isBip47() ? node.getWallet().getMasterWallet() : node.getWallet();
        if(wallet.getKeystores().size() != 1) {
            throw new IllegalStateException("Cannot mix outputs from a wallet with multiple keystores");
        }

        SamouraiNetwork samouraiNetwork = AppServices.getWhirlpoolServices().getSamouraiNetwork();
        boolean testnet = FormatsUtilGeneric.getInstance().isTestNet(samouraiNetwork.getParams());

        UnspentOutput.Xpub xpub = new UnspentOutput.Xpub();
        ExtendedKey.Header header = testnet ? ExtendedKey.Header.tpub : ExtendedKey.Header.xpub;
        xpub.m = wallet.getKeystores().get(0).getExtendedPublicKey().toString(header);
        xpub.path = node.getWallet().isBip47() ? null : node.getDerivationPath().toUpperCase(Locale.ROOT);

        out.xpub = xpub;

        return out;
    }

    public void refreshTorCircuits() {
        AppServices.getHttpClientService().changeIdentity();
    }

    public String getScode() {
        return config.getScode();
    }

    public void setScode(String scode) {
        config.setScode(scode);
    }

    public Tx0FeeTarget getTx0FeeTarget() {
        return tx0FeeTarget;
    }

    public void setTx0FeeTarget(Tx0FeeTarget tx0FeeTarget) {
        this.tx0FeeTarget = tx0FeeTarget;
    }

    public Tx0FeeTarget getMixFeeTarget() {
        return mixFeeTarget;
    }

    public void setMixFeeTarget(Tx0FeeTarget mixFeeTarget) {
        this.mixFeeTarget = mixFeeTarget;
    }

    public String getWalletId() {
        return walletId;
    }

    public String getMixToWalletId() {
        return mixToWalletId;
    }

    public void setResyncMixesDone(boolean resyncMixesDone) {
        this.resyncMixesDone = resyncMixesDone;
    }

    public void setPostmixIndexRange(String indexRange) {
        if(indexRange != null) {
            try {
                config.setIndexRangePostmix(IndexRange.valueOf(indexRange));
            } catch(Exception e) {
                log.error("Invalid index range " + indexRange);
            }
        }
    }

    public void setMixToWallet(String mixToWalletId, Integer minMixes) {
        if(mixToWalletId == null) {
            config.setExternalDestination(null);
        } else {
            Wallet mixToWallet = getWallet(mixToWalletId);
            if(mixToWallet == null) {
                throw new IllegalStateException("Cannot find mix to wallet with id " + mixToWalletId);
            }

            int mixes = minMixes == null ? DEFAULT_MIXTO_MIN_MIXES : minMixes;

            IPostmixHandler postmixHandler = new SparrowPostmixHandler(whirlpoolWalletService, mixToWallet, KeyPurpose.RECEIVE);
            ExternalDestination externalDestination = new ExternalDestination(postmixHandler, 0, mixes, DEFAULT_MIXTO_RANDOM_FACTOR);
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

    public Duration getStartupServiceDelay() {
        return startupServiceDelay;
    }

    public void setStartupServiceDelay(Duration startupServiceDelay) {
        this.startupServiceDelay = startupServiceDelay;
    }

    @Subscribe
    public void onMixSuccess(MixSuccessEvent e) {
        WhirlpoolUtxo whirlpoolUtxo = e.getMixParams().getWhirlpoolUtxo();
        WalletUtxo walletUtxo = getUtxo(whirlpoolUtxo);
        if(walletUtxo != null) {
            log.debug("Mix success, new utxo " + e.getReceiveUtxo().getHash() + ":" + e.getReceiveUtxo().getIndex());
            Platform.runLater(() -> EventManager.get().post(new WhirlpoolMixSuccessEvent(walletUtxo.wallet, walletUtxo.utxo, e.getReceiveUtxo(), getReceiveNode(e, walletUtxo))));
        }
    }

    private WalletNode getReceiveNode(MixSuccessEvent e, WalletUtxo walletUtxo) {
        for(WalletNode walletNode : walletUtxo.wallet.getNode(KeyPurpose.RECEIVE).getChildren()) {
            if(walletNode.getAddress().toString().equals(e.getReceiveDestination().getAddress())) {
                return walletNode;
            }
        }

        return null;
    }

    @Subscribe
    public void onMixFail(MixFailEvent e) {
        WhirlpoolUtxo whirlpoolUtxo = e.getMixParams().getWhirlpoolUtxo();
        WalletUtxo walletUtxo = getUtxo(whirlpoolUtxo);
        if(walletUtxo != null) {
            log.debug("Mix failed for utxo " + whirlpoolUtxo.getUtxo().tx_hash + ":" + whirlpoolUtxo.getUtxo().tx_output_n + " " + e.getMixFailReason());
            Platform.runLater(() -> EventManager.get().post(new WhirlpoolMixEvent(walletUtxo.wallet, walletUtxo.utxo, e.getMixFailReason(), e.getError())));
        }
    }

    @Subscribe
    public void onMixProgress(MixProgressEvent e) {
        WhirlpoolUtxo whirlpoolUtxo = e.getMixParams().getWhirlpoolUtxo();
        MixProgress mixProgress = whirlpoolUtxo.getUtxoState().getMixProgress();
        WalletUtxo walletUtxo = getUtxo(whirlpoolUtxo);
        if(walletUtxo != null && isMixing()) {
            log.debug("Mix progress for utxo " + whirlpoolUtxo.getUtxo().tx_hash + ":" + whirlpoolUtxo.getUtxo().tx_output_n + " " + whirlpoolUtxo.getMixsDone() + " " + mixProgress.getMixStep() + " " + whirlpoolUtxo.getUtxoState().getStatus());
            Platform.runLater(() -> EventManager.get().post(new WhirlpoolMixEvent(walletUtxo.wallet, walletUtxo.utxo, mixProgress)));
        }
    }

    @Subscribe
    public void onWalletStart(WalletStartEvent e) {
        if(e.getWhirlpoolWallet() == whirlpoolWalletService.whirlpoolWallet()) {
            log.info("Mixing to " + e.getWhirlpoolWallet().getConfig().getExternalDestination());
            mixingProperty.set(true);

            if(resyncMixesDone) {
                Wallet wallet = AppServices.get().getWallet(walletId);
                if(wallet != null) {
                    Wallet postmixWallet = getStandardAccountWallet(SamouraiAccount.POSTMIX, wallet);
                    resyncMixesDone(this, postmixWallet);
                    resyncMixesDone = false;
                }
            }
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
        private final List<UtxoEntry> utxoEntries;

        public Tx0PreviewsService(Whirlpool whirlpool, List<UtxoEntry> utxoEntries) {
            this.whirlpool = whirlpool;
            this.utxoEntries = utxoEntries;
        }

        @Override
        protected Task<Tx0Previews> createTask() {
            return new Task<>() {
                protected Tx0Previews call() throws Exception {
                    updateProgress(-1, 1);
                    updateMessage("Fetching premix preview...");

                    Collection<UnspentOutput> utxos = utxoEntries.stream().map(utxoEntry -> Whirlpool.getUnspentOutput(utxoEntry.getNode(), utxoEntry.getBlockTransaction(), (int)utxoEntry.getHashIndex().getIndex())).collect(Collectors.toList());
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
                    return Sha256Hash.wrap(tx0.getTx().getHashAsString());
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

                    try {
                        whirlpool.startingProperty.set(true);
                        WhirlpoolWallet whirlpoolWallet = whirlpool.getWhirlpoolWallet();
                        if(AppServices.onlineProperty().get()) {
                            whirlpoolWallet.startAsync().subscribeOn(Schedulers.io()).observeOn(JavaFxScheduler.platform()).subscribe();
                        }

                        return whirlpoolWallet;
                    } finally {
                        whirlpool.startingProperty.set(false);
                    }
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

                    try {
                        whirlpool.stoppingProperty.set(true);
                        whirlpool.shutdown();
                        return true;
                    } finally {
                        whirlpool.stoppingProperty.set(false);
                    }
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
