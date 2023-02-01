package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.github.arteam.simplejsonrpc.client.JsonRpcClient;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.CormorantPruneStatusEvent;
import com.sparrowwallet.sparrow.event.CormorantScanStatusEvent;
import com.sparrowwallet.sparrow.event.CormorantSyncStatusEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.Bwt;
import com.sparrowwallet.sparrow.net.CoreAuthType;
import com.sparrowwallet.sparrow.net.cormorant.Cormorant;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.sparrow.net.cormorant.electrum.ElectrumBlockHeader;
import com.sparrowwallet.sparrow.net.cormorant.electrum.ScriptHashStatus;
import com.sparrowwallet.sparrow.net.cormorant.index.Store;
import com.sparrowwallet.drongo.protocol.*;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class BitcoindClient {
    private static final Logger log = LoggerFactory.getLogger(BitcoindClient.class);

    public static final String CORE_WALLET_NAME = "cormorant";
    private static final int DEFAULT_GAP_LIMIT = 1000;
    private static final int POSTMIX_GAP_LIMIT = 4000;

    private final JsonRpcClient jsonRpcClient;
    private final Timer timer = new Timer(true);
    private final Store store = new Store();

    private NetworkInfo networkInfo;
    private String lastBlock;
    private ElectrumBlockHeader tip;

    private final Map<String, Lock> descriptorLocks = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, ScanDate> importedDescriptors = Collections.synchronizedMap(new HashMap<>());

    private final Map<String, Date> descriptorBirthDates = new HashMap<>();

    private boolean initialized;
    private boolean stopped;

    private Exception lastPollException;

    private boolean pruned;
    private boolean prunedWarningShown = false;
    private boolean legacyWalletExists;

    private final Lock syncingLock = new ReentrantLock();
    private final Condition syncingCondition = syncingLock.newCondition();
    private boolean syncing;

    private final Lock scanningLock = new ReentrantLock();
    private final Set<String> scanningDescriptors = Collections.synchronizedSet(new HashSet<>());

    private final Lock initialImportLock = new ReentrantLock();
    private final Condition initialImportCondition = initialImportLock.newCondition();
    private boolean initialImportStarted;

    public BitcoindClient() {
        BitcoindTransport bitcoindTransport;

        Config config = Config.get();
        if((config.getCoreAuthType() == CoreAuthType.COOKIE || config.getCoreAuth() == null || config.getCoreAuth().length() < 2) && config.getCoreDataDir() != null) {
            bitcoindTransport = new BitcoindTransport(config.getCoreServer(), CORE_WALLET_NAME, config.getCoreDataDir());
        } else {
            bitcoindTransport = new BitcoindTransport(config.getCoreServer(), CORE_WALLET_NAME, config.getCoreAuth());
        }

        this.jsonRpcClient = new JsonRpcClient(bitcoindTransport);
    }

    public void initialize() throws CormorantBitcoindException {
        networkInfo = getBitcoindService().getNetworkInfo();
        if(networkInfo.version() < 240000) {
            throw new CormorantBitcoindException("Bitcoin Core versions older than v24 are not supported");
        }

        BlockchainInfo blockchainInfo = getBitcoindService().getBlockchainInfo();
        pruned = blockchainInfo.pruned();
        VerboseBlockHeader blockHeader = getBitcoindService().getBlockHeader(blockchainInfo.bestblockhash());
        tip = blockHeader.getBlockHeader();
        timer.schedule(new PollTask(), 5000, 5000);

        if(blockchainInfo.initialblockdownload()) {
            syncingLock.lock();
            try {
                syncing = true;
                syncingCondition.await();

                if(syncing) {
                    if(lastPollException instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new RuntimeException("Error while waiting for sync to complete", lastPollException);
                }
            } catch(InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for sync to complete");
            } finally {
                syncingLock.unlock();
            }

            blockchainInfo = getBitcoindService().getBlockchainInfo();
            blockHeader = getBitcoindService().getBlockHeader(blockchainInfo.bestblockhash());
            tip = blockHeader.getBlockHeader();
        }

        ListWalletDirResult listWalletDirResult = getBitcoindService().listWalletDir();
        boolean exists = listWalletDirResult.wallets().stream().anyMatch(walletDirResult -> walletDirResult.name().equals(CORE_WALLET_NAME));
        legacyWalletExists = listWalletDirResult.wallets().stream().anyMatch(walletDirResult -> walletDirResult.name().equals(Bwt.DEFAULT_CORE_WALLET));

        if(!exists) {
            getBitcoindService().createWallet(CORE_WALLET_NAME, true, true, "", true, true, true, false);
        } else {
            List<String> wallets = getBitcoindService().listWallets();
            if(!wallets.contains(CORE_WALLET_NAME)) {
                getBitcoindService().loadWallet(CORE_WALLET_NAME, true);
            }
        }

        ListSinceBlock listSinceBlock = getListSinceBlock(null);
        updateStore(listSinceBlock);
    }

    private ListSinceBlock getListSinceBlock(String blockHash) {
        return getBitcoindService().listSinceBlock(blockHash, 1, true, true, true);
    }

    public void importWallets(Collection<Wallet> wallets) throws ImportFailedException {
        importDescriptors(getWalletDescriptors(wallets));
    }

    public void importWallet(Wallet wallet) throws ImportFailedException {
        //To avoid unnecessary rescans, get all related wallets
        importWallets(wallet.isMasterWallet() ? wallet.getAllWallets() : wallet.getMasterWallet().getAllWallets());
    }

    public void importAddress(Address address, Date since) {
        Map<String, ScanDate> outputDescriptors = new HashMap<>();
        String addressOutputDescriptor = OutputDescriptor.toDescriptorString(address);
        outputDescriptors.put(OutputDescriptor.normalize(addressOutputDescriptor), new ScanDate(since, null, false));
        importDescriptors(outputDescriptors);
    }

    private Map<String, ScanDate> getWalletDescriptors(Collection<Wallet> wallets) throws ImportFailedException {
        List<Wallet> validWallets = wallets.stream().filter(Wallet::isValid).collect(Collectors.toList());

        Date earliestBirthDate = validWallets.stream().map(Wallet::getBirthDate).filter(Objects::nonNull).sorted().findFirst().orElse(null);
        Map<String, ScanDate> outputDescriptors = new LinkedHashMap<>();
        for(Wallet wallet : validWallets) {
            if(pruned) {
                Optional<Date> optPrunedDate = getPrunedDate();
                if(optPrunedDate.isPresent() && earliestBirthDate != null) {
                    Date prunedDate = optPrunedDate.get();
                    if(earliestBirthDate.before(prunedDate)) {
                        if(!prunedWarningShown) {
                            prunedWarningShown = true;
                            Platform.runLater(() -> EventManager.get().post(new CormorantPruneStatusEvent("Error: Wallet birthday earlier than Bitcoin Core prune date", wallet, earliestBirthDate, prunedDate, legacyWalletExists)));
                        }
                        throw new ImportFailedException("Wallet birthday earlier than prune date");
                    }
                }
            }

            String receiveOutputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.RECEIVE).toString(false, false);
            addOutputDescriptor(outputDescriptors, receiveOutputDescriptor, wallet, KeyPurpose.RECEIVE, earliestBirthDate);
            String changeOutputDescriptor = OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.CHANGE).toString(false, false);
            addOutputDescriptor(outputDescriptors, changeOutputDescriptor, wallet, KeyPurpose.CHANGE, earliestBirthDate);

            if(wallet.isMasterWallet() && wallet.hasPaymentCode()) {
                Wallet notificationWallet = wallet.getNotificationWallet();
                WalletNode notificationNode = notificationWallet.getNode(KeyPurpose.NOTIFICATION);
                String notificationOutputDescriptor = OutputDescriptor.toDescriptorString(notificationNode.getAddress());
                addOutputDescriptor(outputDescriptors, notificationOutputDescriptor, wallet, null, earliestBirthDate);

                for(Wallet childWallet : wallet.getChildWallets()) {
                    if(childWallet.isNested()) {
                        Wallet copyChildWallet = childWallet.copy();
                        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
                            WalletNode purposeNode = copyChildWallet.getNode(keyPurpose);
                            int addressCount = purposeNode.getChildren().size();
                            int gapLimit = ((int)Math.floor(addressCount / 10.0) * 10) + 10;
                            purposeNode.fillToIndex(gapLimit - 1);
                            for(WalletNode addressNode : purposeNode.getChildren()) {
                                String addressOutputDescriptor = OutputDescriptor.toDescriptorString(addressNode.getAddress());
                                addOutputDescriptor(outputDescriptors, addressOutputDescriptor, copyChildWallet, null, earliestBirthDate);
                            }
                        }
                    }
                }
            }
        }

        return outputDescriptors;
    }

    private void addOutputDescriptor(Map<String, ScanDate> outputDescriptors, String outputDescriptor, Wallet wallet, KeyPurpose keyPurpose, Date earliestBirthDate) {
        String normalizedDescriptor = OutputDescriptor.normalize(outputDescriptor);
        ScanDate scanDate = getScanDate(normalizedDescriptor, wallet, keyPurpose, earliestBirthDate);
        outputDescriptors.put(normalizedDescriptor, scanDate);
    }

    private Optional<Date> getPrunedDate() {
        BlockchainInfo blockchainInfo = getBitcoindService().getBlockchainInfo();
        if(blockchainInfo.pruned()) {
            String pruneBlockHash = getBitcoindService().getBlockHash(blockchainInfo.pruneheight());
            VerboseBlockHeader pruneBlockHeader = getBitcoindService().getBlockHeader(pruneBlockHash);
            return Optional.of(new Date(pruneBlockHeader.time() * 1000));
        }

        return Optional.empty();
    }

    private ScanDate getScanDate(String normalizedDescriptor, Wallet wallet, KeyPurpose keyPurpose, Date earliestBirthDate) {
        Integer range = (keyPurpose == null ? null : wallet.getFreshNode(keyPurpose).getIndex() + getGapLimit(wallet, keyPurpose));

        //Force a rescan if loading a wallet with a birthday later than existing transactions, or if the wallet birthdate has been set or changed to an earlier date from the last check
        boolean forceRescan = false;
        Date txBirthDate = wallet.getTransactions().values().stream().map(BlockTransactionHash::getDate).filter(Objects::nonNull).min(Date::compareTo).orElse(null);
        Date lastBirthDate = descriptorBirthDates.get(normalizedDescriptor);
        if((wallet.getBirthDate() != null && txBirthDate != null && wallet.getBirthDate().before(txBirthDate))
                || (descriptorBirthDates.containsKey(normalizedDescriptor) && earliestBirthDate != null && (lastBirthDate == null || earliestBirthDate.before(lastBirthDate)))) {
            forceRescan = true;
        }

        return new ScanDate(earliestBirthDate, range, forceRescan);
    }

    private int getGapLimit(Wallet wallet, KeyPurpose keyPurpose) {
        return wallet.getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX && keyPurpose == KeyPurpose.RECEIVE ? POSTMIX_GAP_LIMIT : DEFAULT_GAP_LIMIT;
    }

    private void importDescriptors(Map<String, ScanDate> descriptors) {
        //Sort descriptors in alphanumeric order to avoid deadlocks, particularly with BIP47 wallets
        Set<String> sortedDescriptors = new TreeSet<>(descriptors.keySet());
        for(String descriptor : sortedDescriptors) {
            Lock lock = descriptorLocks.computeIfAbsent(descriptor, desc -> new ReentrantLock());
            lock.lock();
            descriptorBirthDates.put(descriptor, descriptors.get(descriptor).rescanSince);
        }

        signalInitialImportStarted();

        try {
            Set<String> addedDescriptors = addDescriptors(descriptors);
            if(!addedDescriptors.isEmpty()) {
                ListSinceBlock listSinceBlock = getListSinceBlock(null);
                updateStore(listSinceBlock, addedDescriptors);
            }
        } finally {
            for(String descriptor : descriptors.keySet()) {
                Lock lock = descriptorLocks.get(descriptor);
                lock.unlock();
            }
        }
    }

    private Set<String> addDescriptors(Map<String, ScanDate> descriptors) {
        boolean forceRescan = descriptors.values().stream().anyMatch(scanDate -> scanDate.forceRescan);
        if(!initialized || forceRescan) {
            ListDescriptorsResult listDescriptorsResult = getBitcoindService().listDescriptors(false);
            for(ListDescriptorResult result : listDescriptorsResult.descriptors()) {
                String descriptor = OutputDescriptor.normalize(result.desc());
                ScanDate previousScanDate = importedDescriptors.get(descriptor);
                Date scanDate = result.getScanDate();
                Date rescanSince = scanDate != null ? scanDate : (previousScanDate != null ? previousScanDate.rescanSince : null);
                Integer range = result.range() == null ? null : result.range().get(result.range().size() - 1);
                importedDescriptors.put(descriptor, new ScanDate(rescanSince, range, false));
            }
        }

        Map<String, ScanDate> importingDescriptors = new LinkedHashMap<>(descriptors);
        importingDescriptors.keySet().removeAll(importedDescriptors.keySet());
        for(Map.Entry<String, ScanDate> entry : descriptors.entrySet()) {
            if(importingDescriptors.containsKey(entry.getKey())) {
                continue;
            }

            ScanDate scanDate = entry.getValue();
            if(scanDate.forceRescan) {
                ScanDate importedScanDate = importedDescriptors.get(entry.getKey());
                if(scanDate.rescanSince != null && (importedScanDate == null || importedScanDate.rescanSince == null || scanDate.rescanSince.before(importedScanDate.rescanSince))) {
                    importingDescriptors.put(entry.getKey(), new ScanDate(scanDate.rescanSince, importedScanDate != null ? importedScanDate.range : scanDate.range, false));
                }
            }
        }

        if(!importingDescriptors.isEmpty()) {
            log.debug("Importing descriptors " + importingDescriptors);

            List<ImportDescriptor> importDescriptors = importingDescriptors.entrySet().stream()
                    .map(entry -> {
                        ScanDate scanDate = entry.getValue();
                        if(entry.getKey().contains("/0/*")) {
                            return new ImportRangedDescriptor(entry.getKey(), true, scanDate.range(), scanDate.getTimestamp(), false);
                        } else if(entry.getKey().contains("/1/*")) {
                            return new ImportRangedDescriptor(entry.getKey(), false, scanDate.range(), scanDate.getTimestamp(), true);
                        }
                        return new ImportDescriptor(entry.getKey(), false, entry.getValue().getTimestamp(), true);
                    }).toList();

            List<ImportDescriptorResult> results;
            scanningLock.lock();
            try {
                scanningDescriptors.addAll(importingDescriptors.keySet());
                Platform.runLater(() -> EventManager.get().post(new CormorantScanStatusEvent("Scanning (0%)", getScanningWallets(), 0, null)));
                results = getBitcoindService().importDescriptors(importDescriptors);
            } finally {
                scanningLock.unlock();
                Set<Wallet> scanningWallets = getScanningWallets();
                Platform.runLater(() -> EventManager.get().post(new CormorantScanStatusEvent("Scanning completed", scanningWallets, 100, Duration.ZERO)));
                scanningDescriptors.clear();
            }

            for(int i = 0; i < importDescriptors.size(); i++) {
                ImportDescriptor importDescriptor = importDescriptors.get(i);
                ImportDescriptorResult importDescriptorResult = results.get(i);
                if(importDescriptorResult.success()) {
                    importedDescriptors.put(importDescriptor.getDesc(), importingDescriptors.get(importDescriptor.getDesc()));
                } else {
                    log.error("Error importing descriptor " + importDescriptor.getDesc() + ": " + importDescriptorResult);
                }
            }
        }

        initialized = true;
        return importingDescriptors.keySet();
    }

    public void stop() {
        timer.cancel();
        stopped = true;
    }

    private void updateStore(ListSinceBlock listSinceBlock, Set<String> descriptors) {
        listSinceBlock.removed().removeIf(lt -> lt.parent_descs() != null && lt.parent_descs().stream().map(OutputDescriptor::normalize).noneMatch(descriptors::contains));
        listSinceBlock.transactions().removeIf(lt -> lt.parent_descs() != null && lt.parent_descs().stream().map(OutputDescriptor::normalize).noneMatch(descriptors::contains));
        updateStore(listSinceBlock);
    }

    private synchronized void updateStore(ListSinceBlock listSinceBlock) {
        Set<String> updatedScriptHashes = new HashSet<>();

        for(ListTransaction removedTransaction : listSinceBlock.removed()) {
            if(removedTransaction.confirmations() < 0) {
                updatedScriptHashes.addAll(store.purgeTransaction(removedTransaction.txid()));
            }
        }

        List<ListTransaction> sentTransactions = new ArrayList<>();
        Map<String, Boolean> conflictCache = new HashMap<>();

        for(ListTransaction listTransaction : listSinceBlock.transactions()) {
            if(isConflicted(listTransaction, conflictCache)) {
                updatedScriptHashes.addAll(store.purgeTransaction(listTransaction.txid()));
                continue;
            }

            try {
                if(listTransaction.category() == Category.receive) {
                    //Transactions received to an address can be added directly
                    Address address = Address.fromString(listTransaction.address());
                    String updatedScriptHash = store.addAddressTransaction(address, listTransaction);
                    if(updatedScriptHash != null) {
                        updatedScriptHashes.add(updatedScriptHash);
                    }
                } else if(listTransaction.category() == Category.send) {
                    //Need to determine the address the transaction was sent from
                    //Cache until all receive txes are processed
                    sentTransactions.add(listTransaction);
                }
            } catch(InvalidAddressException e) {
                //ignore
            }
        }

        for(ListTransaction sentTransaction : sentTransactions) {
            Set<HashIndex> spentOutputs = store.getSpentOutputs().computeIfAbsent(sentTransaction.txid(), txid -> {
                String txhex = getTransaction(txid);
                Transaction tx = new Transaction(Utils.hexToBytes(txhex));
                return tx.getInputs().stream().map(txInput -> new HashIndex(txInput.getOutpoint().getHash(), txInput.getOutpoint().getIndex())).collect(Collectors.toSet());
            });

            boolean foundFundingAddress = false;
            for(HashIndex spentOutput : spentOutputs) {
                Address fundingAddress = store.getFundingAddress(spentOutput);
                if(fundingAddress != null) {
                    String updatedScriptHash = store.addAddressTransaction(fundingAddress, sentTransaction);
                    if(updatedScriptHash != null) {
                        updatedScriptHashes.add(updatedScriptHash);
                    }
                    foundFundingAddress = true;
                }
            }

            if(!foundFundingAddress) {
                log.error("Could not find a funding address for wallet spend tx " + sentTransaction.txid());
            }
        }

        syncMempool(!listSinceBlock.lastblock().equals(lastBlock));
        updatedScriptHashes.addAll(store.updateMempoolTransactions());

        lastBlock = listSinceBlock.lastblock();

        for(String updatedScriptHash : updatedScriptHashes) {
            Cormorant.getEventBus().post(new ScriptHashStatus(updatedScriptHash, store.getStatus(updatedScriptHash)));
        }
    }

    private String getTransaction(String txid) {
        try {
            return getBitcoindService().getTransaction(txid, false).get("hex").toString();
        } catch(JsonRpcException e) {
            return getBitcoindService().getRawTransaction(txid, false).toString();
        }
    }

    private void syncMempool(boolean forceRefresh) {
        Map<String, MempoolEntry> mempoolEntries = store.getMempoolEntries();

        for(String txid : new HashSet<>(mempoolEntries.keySet())) {
            if(forceRefresh || mempoolEntries.get(txid) == null) {
                try {
                    MempoolEntry mempoolEntry = getBitcoindService().getMempoolEntry(txid);
                    mempoolEntries.put(txid, mempoolEntry);
                } catch(JsonRpcException e) {
                    mempoolEntries.remove(txid);
                }
            }
        }
    }

    private boolean isConflicted(ListTransaction listTransaction, Map<String, Boolean> conflictCache) {
        if(listTransaction.confirmations() == 0 && !listTransaction.walletconflicts().isEmpty()) {
            Boolean active = conflictCache.computeIfAbsent(listTransaction.txid(), txid -> {
                try {
                    getBitcoindService().getMempoolEntry(txid);
                    return true;
                } catch(JsonRpcException e) {
                    return false;
                }
            });

            if(active) {
                for(String conflictedTxid : listTransaction.walletconflicts()) {
                    conflictCache.put(conflictedTxid, false);
                }
            }

            return !active;
        } else {
            return listTransaction.confirmations() < 0;
        }
    }

    public void waitUntilInitialImportStarted() {
        initialImportLock.lock();
        try {
            if(!initialImportStarted) {
                initialImportCondition.await();
            }
        } catch(InterruptedException e) {
            //ignore
        } finally {
            initialImportLock.unlock();
        }
    }

    public void signalInitialImportStarted() {
        if(!initialImportStarted) {
            initialImportLock.lock();
            try {
                initialImportStarted = true;
                initialImportCondition.signal();
            } finally {
                initialImportLock.unlock();
            }
        }
    }

    public Store getStore() {
        return store;
    }

    public BitcoindClientService getBitcoindService() {
        return jsonRpcClient.onDemand(BitcoindClientService.class);
    }

    public NetworkInfo getNetworkInfo() {
        return networkInfo;
    }

    public ElectrumBlockHeader getTip() {
        return tip;
    }

    private class PollTask extends TimerTask {
        @Override
        public void run() {
            if(stopped) {
                timer.cancel();
            }

            try {
                if(syncing) {
                    BlockchainInfo blockchainInfo = getBitcoindService().getBlockchainInfo();
                    if(blockchainInfo.initialblockdownload()) {
                        int percent = blockchainInfo.getProgressPercent();
                        Date tipDate = blockchainInfo.getTip();
                        Platform.runLater(() -> EventManager.get().post(new CormorantSyncStatusEvent("Syncing" + (percent < 100 ? " (" + percent + "%)" : ""), percent, tipDate)));
                        return;
                    } else {
                        syncing = false;
                        syncingLock.lock();
                        try {
                            syncingCondition.signal();
                        } finally {
                            syncingLock.unlock();
                        }
                    }
                }

                if(lastBlock != null && tip != null) {
                    String blockhash = getBitcoindService().getBlockHash(tip.height());
                    if(!lastBlock.equals(blockhash)) {
                        log.warn("Reorg detected, block height " + tip.height() + " was " + lastBlock + " and now is " + blockhash);
                        lastBlock = null;
                    }
                }

                ListSinceBlock listSinceBlock = getListSinceBlock(lastBlock);
                String currentBlock = lastBlock;
                updateStore(listSinceBlock);

                if(currentBlock == null || !currentBlock.equals(listSinceBlock.lastblock())) {
                    VerboseBlockHeader blockHeader = getBitcoindService().getBlockHeader(listSinceBlock.lastblock());
                    tip = blockHeader.getBlockHeader();
                    Cormorant.getEventBus().post(tip);
                }

                if(scanningLock.tryLock()) {
                    scanningLock.unlock();
                } else {
                    WalletInfo walletInfo = getBitcoindService().getWalletInfo();
                    if(walletInfo.scanning().isScanning()) {
                        Set<Wallet> scanningWallets = getScanningWallets();
                        int percent = walletInfo.scanning().getPercent();
                        Duration remainingDuration = walletInfo.scanning().getRemaining();
                        if(percent > 0) {
                            Platform.runLater(() -> EventManager.get().post(new CormorantScanStatusEvent("Scanning" + (percent < 100 ? " (" + percent + "%)" : ""), scanningWallets, percent, remainingDuration)));
                        }
                    }
                }
            } catch(Exception e) {
                lastPollException = e;
                log.warn("Error polling Bitcoin Core: " + e.getMessage());

                if(syncing) {
                    syncingLock.lock();
                    try {
                        syncingCondition.signal();
                    } finally {
                        syncingLock.unlock();
                    }
                }
            }
        }
    }

    private Set<Wallet> getScanningWallets() {
        Set<Wallet> scanningWallets = new HashSet<>();
        Set<Wallet> openWallets = AppServices.get().getOpenWallets().keySet();
        for(Wallet openWallet : openWallets) {
            String normalizedDescriptor = OutputDescriptor.normalize(OutputDescriptor.getOutputDescriptor(openWallet, KeyPurpose.RECEIVE).toString(false, false));
            if(scanningDescriptors.contains(normalizedDescriptor)) {
                scanningWallets.add(openWallet);
            }
        }

        return scanningWallets;
    }

    private record ScanDate(Date rescanSince, Integer range, boolean forceRescan) {
        public Object getTimestamp() {
            return rescanSince == null ? "now" : rescanSince.getTime() / 1000;
        }
    }
}
