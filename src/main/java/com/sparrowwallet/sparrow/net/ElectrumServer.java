package com.sparrowwallet.sparrow.net;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.Version;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.bip47.InvalidPaymentCodeException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.silentpayments.InvalidSilentPaymentException;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentScanAddress;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentScanMatch;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentUtils;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.BlockSummary;
import com.sparrowwallet.sparrow.ChainTip;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.net.cormorant.Cormorant;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.CormorantBitcoindException;
import com.sparrowwallet.sparrow.net.cormorant.bitcoind.CormorantBitcoindUnsupportedException;
import com.sparrowwallet.sparrow.paynym.PayNym;
import com.sparrowwallet.sparrow.paynym.PayNymService;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ElectrumServer {
    private static final Logger log = LoggerFactory.getLogger(ElectrumServer.class);

    static final String[] SUPPORTED_VERSIONS = new String[]{"1.3", "1.4.2"};

    private static final Version ELECTRS_MIN_BATCHING_VERSION = new Version("0.9.0");

    private static final Version FULCRUM_MIN_BATCHING_VERSION = new Version("1.6.0");

    private static final Version MEMPOOL_ELECTRS_MIN_BATCHING_VERSION = new Version("3.1.0");

    public static final String CORE_ELECTRUM_HOST = "127.0.0.1";

    private static final int MINIMUM_BROADCASTS = 2;

    private static final int[] NO_LABELS = new int[0];

    public static final BlockTransaction UNFETCHABLE_BLOCK_TRANSACTION = new BlockTransaction(Sha256Hash.ZERO_HASH, 0, null, null, null);

    static CloseableTransport transport;

    private static final Map<String, List<String>> subscribedScriptHashes = new ConcurrentHashMap<>();

    private static Server previousServer;

    static final Map<String, String> retrievedScriptHashes = Collections.synchronizedMap(new HashMap<>());

    private static final Map<Sha256Hash, BlockTransaction> retrievedTransactions = new ConcurrentHashMap<>();

    static final Map<Integer, BlockHeader> retrievedBlockHeaders = new ConcurrentHashMap<>();

    private static final Map<Sha256Hash, BlockTransaction> broadcastedTransactions = new ConcurrentHashMap<>();

    private static final Set<String> sameHeightTxioScriptHashes = ConcurrentHashMap.newKeySet();

    static final Set<String> reorgInvalidatedScriptHashes = ConcurrentHashMap.newKeySet();

    static volatile HeaderStore headerStore;

    //Not the ElectrumServer class monitor that getTransport() uses: a store load or re-walk would then block every transport acquisition for its duration
    private static final Object headerStoreLock = new Object();

    //Serialises fetch and append across the header sync service and the wallet history threads, which sync concurrently by design
    private static final Object headerSyncLock = new Object();

    //Session cache of headers below the last pin, verified by hash linkage to it and deliberately never persisted
    static final Map<Integer, BlockHeader> verifiedHistoricalHeaders = new ConcurrentHashMap<>();

    //The deepest fork point the store has been rewound to this session, at or above which a stored height may have been proven against an orphaned
    //header. Written only under headerSyncLock, which is what makes the min in reconcile atomic; volatile is for the readers that do not take it
    static volatile int lastReorgForkHeight = Integer.MAX_VALUE;

    private static final Map<Integer, WalletSyncLock> walletSyncLocks = Collections.synchronizedMap(new HashMap<>());

    private static final Map<String, SilentPaymentsScanCache> spScanCaches = new ConcurrentHashMap<>();

    private static final int TAPROOT_ACTIVATION_HEIGHT = 709632;

    private static final int HEADERS_CHUNK_SIZE = HeaderChainState.RETARGET_INTERVAL;

    //A reorg deeper than this is a global event rather than a client concern, and the store keeps the heavier chain it already has
    private static final int MAX_REORG_DEPTH = 100;

    //Consensus rejects blocks timestamped more than 2 hours in the future, extended here to allow for local clock skew
    private static final long MAXIMUM_FUTURE_TIP_TIME_SECS = 4 * 60 * 60;

    //A gap of over 2 hours between mainnet blocks occurs naturally roughly once every 3 years
    private static final long STALE_TIP_WARNING_AGE_MILLIS = 2 * 60 * 60 * 1000;

    private static final long TIP_WARNING_INTERVAL_MILLIS = 60 * 1000;

    private static volatile long lastTipReceivedAt;

    private static volatile boolean staleTipWarned;

    private static volatile boolean invalidTipWarned;

    private static volatile long lastTipWarningLoggedAt;

    //A server refusing for capacity recovers within these attempts, one that cannot substantiate a height never does. Not final so tests need not wait
    static int proofAttempts = 4;

    static long proofRetryDelayMillis = 2000;

    //Filters the dialogs rather than the verification, so the passes that re-attempt a refused transaction do not raise it again
    static final Set<String> proofWarnedPairs = ConcurrentHashMap.newKeySet();

    //Tracked apart, since being proven wrong must still be raised where the pair was only refused on an earlier pass, while the reverse adds nothing
    static final Set<String> proofsShownFalseWarnedPairs = ConcurrentHashMap.newKeySet();

    private final static Map<String, Integer> subscribedRecent = new ConcurrentHashMap<>();

    private final static Map<String, String> broadcastRecent = new ConcurrentHashMap<>();

    final static Map<String, BlockTransaction> confirmingRecent = new ConcurrentHashMap<>();

    static ElectrumServerRpc electrumServerRpc = new SimpleElectrumServerRpc();

    private static Cormorant cormorant;

    private static Server coreElectrumServer;

    static ServerCapability serverCapability;

    private static final Pattern RPC_WALLET_LOADING_PATTERN = Pattern.compile(".*\"(Wallet loading failed[:.][^\"]*)\".*");

    //Per pass, since one ElectrumServer is built per history task. A demoted height reads as changed to the next of the several gated calls in a
    //pass, so without this memo each would spend the retries on it again
    private final Set<BlockTransactionHash> refusedThisPass = new HashSet<>();

    //Keyed by wallet, since one task runs the master and each nested child. Held no longer than the task: postProofEvents removes each key in the
    //finally that closes the wallet it was added under, and the instance is a local of a Task.call()
    private final Map<Wallet, Set<BlockTransactionHash>> failed = new LinkedHashMap<>();

    private final Map<Wallet, Set<BlockTransactionHash>> refused = new LinkedHashMap<>();

    //The nodes this task demoted a height in. Their outputs disagree with the server's status by construction, so the changed history check must not
    //count them: a wallet whose used nodes are all demoted would otherwise abort into a full refresh on every open, the demotion being stored
    private final Set<String> demotedScriptHashes = new HashSet<>();

    private static synchronized CloseableTransport getTransport() throws ServerException {
        if(transport == null) {
            try {
                Server electrumServer = null;
                File electrumServerCert = null;
                String proxyServer = null;

                if(Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER) {
                    electrumServer = Config.get().getPublicElectrumServer();
                    proxyServer = Config.get().getProxyServer();
                } else if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
                    if(coreElectrumServer == null) {
                        throw new ServerConfigException("Could not connect to Bitcoin Core RPC");
                    }
                    electrumServer = coreElectrumServer;
                    if(previousServer != null && previousServer.getUrl().contains(CORE_ELECTRUM_HOST)) {
                        previousServer = coreElectrumServer;
                    }
                } else if(Config.get().getServerType() == ServerType.ELECTRUM_SERVER) {
                    electrumServer = Config.get().getElectrumServer();
                    electrumServerCert = Config.get().getElectrumServerCert();
                    proxyServer = Config.get().getProxyServer();
                }

                if(electrumServer == null) {
                    throw new ServerConfigException("Electrum server URL not specified");
                }

                if(electrumServerCert != null && !electrumServerCert.exists()) {
                    throw new ServerConfigException("Electrum server certificate file not found");
                }

                Protocol protocol = electrumServer.getProtocol();

                //If changing server, don't rely on previous transaction history
                if(previousServer != null && !electrumServer.equals(previousServer)) {
                    clearPreviousServerState();
                }
                previousServer = electrumServer;

                HostAndPort hostAndPort = electrumServer.getHostAndPort();
                boolean localNetworkAddress = !Protocol.isOnionAddress(hostAndPort) && !PublicElectrumServer.isPublicServer(hostAndPort)
                        && IpAddressMatcher.isLocalNetworkAddress(hostAndPort.getHost());

                if(!localNetworkAddress && Config.get().isUseProxy() && proxyServer != null && !proxyServer.isBlank()) {
                    HostAndPort proxy = HostAndPort.fromString(proxyServer);
                    if(electrumServerCert != null) {
                        transport = protocol.getTransport(hostAndPort, electrumServerCert, proxy);
                    } else {
                        transport = protocol.getTransport(hostAndPort, proxy);
                    }
                } else {
                    if(electrumServerCert != null) {
                        transport = protocol.getTransport(hostAndPort, electrumServerCert);
                    } else {
                        transport = protocol.getTransport(hostAndPort);
                    }
                }
            } catch (Exception e) {
                throw new ServerConfigException(e);
            }
        }

        return transport;
    }

    /**
     * Forgets what the previous server told us on changing to another, including which of its claims were shown unproven: the dialogs ask the user to
     * switch servers, so the new one is judged afresh. Verified headers are not forgotten, being claims about the chain rather than about the server.
     */
    static void clearPreviousServerState() {
        retrievedScriptHashes.clear();
        retrievedTransactions.clear();
        retrievedBlockHeaders.clear();
        reorgInvalidatedScriptHashes.clear();
        proofWarnedPairs.clear();
        proofsShownFalseWarnedPairs.clear();
        walletSyncLocks.values().forEach(syncLock -> syncLock.scriptHashesInitialized = false);
    }

    public void connect() throws ServerException {
        CloseableTransport closeableTransport = getTransport();
        closeableTransport.connect();
    }

    public void ping() throws ServerException {
        electrumServerRpc.ping(getTransport());
    }

    public List<String> getServerVersion() throws ServerException {
        return electrumServerRpc.getServerVersion(getTransport(), "Sparrow", SUPPORTED_VERSIONS);
    }

    public ServerFeatures getServerFeatures() throws ServerException {
        return electrumServerRpc.getServerFeatures(getTransport());
    }

    public String getServerBanner() throws ServerException {
        return electrumServerRpc.getServerBanner(getTransport());
    }

    public BlockHeaderTip subscribeBlockHeaders() throws ServerException {
        return electrumServerRpc.subscribeBlockHeaders(getTransport());
    }

    public static synchronized boolean isConnected() {
        if(transport != null) {
            TcpTransport tcpTransport = (TcpTransport)transport;
            return tcpTransport.isConnected();
        }

        return false;
    }

    public static synchronized void closeActiveConnection() throws ServerException {
        if(transport != null) {
            cancelSilentPaymentScans();
            spScanCaches.clear();
            closeConnection(transport);
            transport = null;
        }
    }

    private static void closeConnection(Closeable closeableTransport) throws ServerException {
        try {
            closeableTransport.close();
        } catch (IOException e) {
            throw new ServerException(e);
        }
    }

    private static void addCalculatedScriptHashes(Wallet wallet) {
        getCalculatedScriptHashes(wallet).forEach(retrievedScriptHashes::putIfAbsent);
    }

    private static void addCalculatedScriptHashes(WalletNode walletNode) {
        Map<String, String> calculatedScriptHashStatuses = new HashMap<>();
        addScriptHashStatus(calculatedScriptHashStatuses, walletNode);
        calculatedScriptHashStatuses.forEach(retrievedScriptHashes::putIfAbsent);
    }

    private static Map<String, String> getCalculatedScriptHashes(Wallet wallet) {
        Map<String, String> storedScriptHashStatuses = new HashMap<>();
        storedScriptHashStatuses.putAll(calculateScriptHashes(wallet, KeyPurpose.RECEIVE));
        storedScriptHashStatuses.putAll(calculateScriptHashes(wallet, KeyPurpose.CHANGE));
        return storedScriptHashStatuses;
    }

    private static Map<String, String> calculateScriptHashes(Wallet wallet, KeyPurpose keyPurpose) {
        Map<String, String> calculatedScriptHashes = new LinkedHashMap<>();
        for(WalletNode walletNode : wallet.getNode(keyPurpose).getChildren()) {
            addScriptHashStatus(calculatedScriptHashes, walletNode);
        }

        return calculatedScriptHashes;
    }

    private static void addScriptHashStatus(Map<String, String> calculatedScriptHashes, WalletNode walletNode) {
        String scriptHash = getScriptHash(walletNode);
        String scriptHashStatus = getScriptHashStatus(scriptHash, walletNode);
        calculatedScriptHashes.put(scriptHash, scriptHashStatus);
    }

    static String getScriptHashStatus(String scriptHash, WalletNode walletNode) {
        List<ScriptHashTx> scriptHashTxes = getScriptHashes(scriptHash, walletNode);
        return getScriptHashStatus(scriptHashTxes);
    }

    private static List<ScriptHashTx> getScriptHashes(String scriptHash, WalletNode walletNode) {
        List<BlockTransactionHashIndex> txos  = new ArrayList<>(walletNode.getTransactionOutputs());
        txos.addAll(walletNode.getTransactionOutputs().stream().filter(BlockTransactionHashIndex::isSpent).map(BlockTransactionHashIndex::getSpentBy).collect(Collectors.toList()));
        Set<Sha256Hash> unique = new HashSet<>(txos.size());
        txos.removeIf(ref -> !unique.add(ref.getHash()));
        txos.sort((txo1, txo2) -> {
            if(txo1.getHeight() != txo2.getHeight()) {
                return txo1.getComparisonHeight() - txo2.getComparisonHeight();
            }

            if(txo1.isSpent() && txo1.getSpentBy().equals(txo2)) {
                return -1;
            }

            if(txo2.isSpent() && txo2.getSpentBy().equals(txo1)) {
                return 1;
            }

            //We cannot further sort by order within a block, so sometimes multiple txos to an address will mean an incorrect status
            //Save a record of these to avoid triggering an AllHistoryChangedEvent based on potentially incorrect calculated statuses
            sameHeightTxioScriptHashes.add(scriptHash);
            return 0;
        });

        return txos.stream().map(txo -> new ScriptHashTx(txo.getHeight(), txo.getHashAsString(), txo.getFee() == null ? 0 : txo.getFee())).toList();
    }

    static String getScriptHashStatus(List<ScriptHashTx> scriptHashTxes) {
        if(!scriptHashTxes.isEmpty()) {
            StringBuilder scriptHashStatus = new StringBuilder();
            for(ScriptHashTx scriptHashTx : scriptHashTxes) {
                scriptHashStatus.append(scriptHashTx.tx_hash).append(":").append(scriptHashTx.height).append(":");
            }

            return Utils.bytesToHex(Sha256Hash.hash(scriptHashStatus.toString().getBytes(StandardCharsets.UTF_8)));
        } else {
            return null;
        }
    }

    public static void clearRetrievedScriptHashes(Wallet wallet) {
        wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().map(ElectrumServer::getScriptHash).forEach(ElectrumServer::clearRetrievedScriptHash);
        wallet.getNode(KeyPurpose.CHANGE).getChildren().stream().map(ElectrumServer::getScriptHash).forEach(ElectrumServer::clearRetrievedScriptHash);
        walletSyncLocks.computeIfAbsent(wallet.hashCode(), w -> new WalletSyncLock()).scriptHashesInitialized = false;
    }

    private static void clearRetrievedScriptHash(String scriptHash) {
        retrievedScriptHashes.remove(scriptHash);
        sameHeightTxioScriptHashes.remove(scriptHash);
    }

    /**
     * Invalidates the cached status of every node holding a transaction output, or a spend of one, above the given fork point, so that the reorganised
     * history is fetched again. A transaction re-included at the same height leaves the server reporting an unchanged status, and without this the
     * node would never be revisited. This touches cache state only, never wallet data.
     * <p>
     * Returns whether any node was invalidated, which is false for a wallet holding nothing above the fork: it can have proven nothing against a
     * header that was discarded, so there is nothing for it to fetch again.
     */
    public static boolean invalidateScriptHashesForReorg(Wallet wallet, int forkHeight) {
        boolean invalidated = invalidateWalletScriptHashesForReorg(wallet, forkHeight);
        for(Wallet childWallet : new ArrayList<>(wallet.getChildWallets())) {
            if(childWallet.isNested()) {
                invalidated |= invalidateWalletScriptHashesForReorg(childWallet, forkHeight);
            }
        }

        return invalidated;
    }

    private static boolean invalidateWalletScriptHashesForReorg(Wallet wallet, int forkHeight) {
        boolean invalidated = false;
        for(Map.Entry<WalletNode, Set<BlockTransactionHashIndex>> entry : wallet.getWalletNodes().entrySet()) {
            if(entry.getValue().stream().anyMatch(txo -> txo.getHeight() > forkHeight || (txo.getSpentBy() != null && txo.getSpentBy().getHeight() > forkHeight))) {
                String scriptHash = getScriptHash(entry.getKey());
                clearRetrievedScriptHash(scriptHash);
                reorgInvalidatedScriptHashes.add(scriptHash);
                invalidated = true;
            }
        }

        return invalidated;
    }

    public boolean fetchAndCalculateHistory(Wallet mainWallet, List<Wallet> filterToWallets, Set<WalletNode> filterToNodes) throws ServerException {
        boolean historyFetched = fetchAndCalculateWalletHistory(mainWallet, filterToWallets, filterToNodes);
        for(Wallet childWallet : new ArrayList<>(mainWallet.getChildWallets())) {
            if(childWallet.isNested()) {
                historyFetched |= fetchAndCalculateWalletHistory(childWallet, filterToWallets, filterToNodes);
            }
        }

        return historyFetched;
    }

    private boolean fetchAndCalculateWalletHistory(Wallet wallet, List<Wallet> filterToWallets, Set<WalletNode> filterToNodes) throws ServerException {
        if(filterToWallets != null && !filterToWallets.contains(wallet)) {
            return false;
        }

        Set<WalletNode> nodes = (filterToNodes == null ? null : filterToNodes.stream().filter(node -> node.getWallet().equals(wallet)).collect(Collectors.toSet()));
        if(filterToNodes != null && nodes.isEmpty()) {
            return false;
        }

        WalletSyncLock walletSyncLock = walletSyncLocks.computeIfAbsent(wallet.hashCode(), w -> new WalletSyncLock());
        synchronized(walletSyncLock) {
            if(!walletSyncLock.scriptHashesInitialized) {
                addCalculatedScriptHashes(wallet);
                walletSyncLock.scriptHashesInitialized = true;
            }

            if(isConnected()) {
                try {
                    //Taken before the fetch, so an invalidation arriving while this pass runs can be told from one the pass is acting on
                    Set<String> invalidatedBeforeFetch = Set.copyOf(reorgInvalidatedScriptHashes);
                    Map<String, String> previousScriptHashes = getCalculatedScriptHashes(wallet);
                    Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = (nodes == null ? getHistory(wallet) : getHistory(wallet, nodes));
                    getReferencedTransactions(wallet, nodeTransactionMap);
                    calculateNodeHistory(wallet, nodeTransactionMap);

                    //A node invalidated by a reorg has no retrieved status left to compare against, so it must not count as changed history below
                    Set<String> invalidatedScriptHashes = Set.copyOf(reorgInvalidatedScriptHashes);

                    //Add all of the script hashes we have now fetched the history for so we don't need to fetch again until the script hash status changes
                    Set<WalletNode> updatedNodes = new HashSet<>();
                    Map<WalletNode, Set<BlockTransactionHashIndex>> walletNodes = wallet.getWalletNodes();
                    for(WalletNode node : (nodes == null ? walletNodes.keySet() : nodes)) {
                        String scriptHash = getScriptHash(node);
                        String subscribedStatus = getSubscribedScriptHashStatus(scriptHash);
                        if(!Objects.equals(subscribedStatus, retrievedScriptHashes.get(scriptHash))) {
                            updatedNodes.add(node);
                        }

                        //A reorg detected while this pass was in flight - which is what happens when a history thread is the one to reconcile -
                        //invalidated data the pass had already fetched, so its status stays cleared for the refresh the reorg triggers. Restoring it
                        //would leave the node looking up to date while it still holds what the orphaned block proved, and nothing would fetch it again
                        if(invalidatedBeforeFetch.contains(scriptHash) || !invalidatedScriptHashes.contains(scriptHash)) {
                            retrievedScriptHashes.put(scriptHash, subscribedStatus);
                        }
                    }

                    //If wallet was not empty, check if all used updated nodes have changed history
                    if(nodes == null && previousScriptHashes.values().stream().anyMatch(Objects::nonNull)) {
                        Set<WalletNode> changedNodes = updatedNodes.stream().filter(node -> {
                            String scriptHash = getScriptHash(node);
                            //A demoted node disagrees with the server because this pass demoted it, which is not the wallet having a different history
                            return !invalidatedScriptHashes.contains(scriptHash) && !demotedScriptHashes.contains(scriptHash);
                        }).collect(Collectors.toSet());
                        if(!changedNodes.isEmpty()
                                && changedNodes.equals(walletNodes.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).map(Map.Entry::getKey).collect(Collectors.toSet()))
                                && !sameHeightTxioScriptHashes.containsAll(changedNodes.stream().map(ElectrumServer::getScriptHash).collect(Collectors.toSet()))) {
                            //All used nodes on a non-empty wallet have changed history. Abort and trigger a full refresh.
                            log.info("All used nodes on a non-empty wallet have changed history. Triggering a full wallet refresh.");
                            throw new AllHistoryChangedException();
                        }
                    }

                    //The exemption lasts for exactly one full fetch, and is cleared only once the check above has passed. Only what was invalidated
                    //before this pass fetched anything is cleared: the rest is for the refresh the reorg triggers, which has not run yet
                    if(nodes == null && !invalidatedBeforeFetch.isEmpty()) {
                        Set<String> walletScriptHashes = walletNodes.keySet().stream().map(ElectrumServer::getScriptHash).collect(Collectors.toSet());
                        reorgInvalidatedScriptHashes.removeAll(invalidatedBeforeFetch.stream().filter(walletScriptHashes::contains).collect(Collectors.toSet()));
                    }

                    //Clear transaction outputs for nodes that have no history - this is useful when a transaction is replaced in the mempool
                    if(nodes != null) {
                        for(WalletNode node : nodes) {
                            String scriptHash = getScriptHash(node);
                            if(retrievedScriptHashes.get(scriptHash) == null && !node.getTransactionOutputs().isEmpty()) {
                                log.debug("Clearing transaction history for " + node);
                                node.getTransactionOutputs().clear();
                            }
                        }
                    }

                    return true;
                } finally {
                    //A finding is demoted, and may already have been written, before a later call in the pass can fail: it must be shown either way
                    postProofEvents(wallet);
                }
            }

            return false;
        }
    }

    public Map<WalletNode, Set<BlockTransactionHash>> getHistory(Wallet wallet) throws ServerException {
        Map<WalletNode, Set<BlockTransactionHash>> receiveTransactionMap = new TreeMap<>();
        getHistory(wallet, KeyPurpose.RECEIVE, receiveTransactionMap);

        Map<WalletNode, Set<BlockTransactionHash>> changeTransactionMap = new TreeMap<>();
        getHistory(wallet, KeyPurpose.CHANGE, changeTransactionMap);

        receiveTransactionMap.putAll(changeTransactionMap);
        return receiveTransactionMap;
    }

    public Map<WalletNode, Set<BlockTransactionHash>> getHistory(Wallet wallet, Collection<WalletNode> nodes) throws ServerException {
        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new TreeMap<>();

        Set<WalletNode> historyNodes = new HashSet<>(nodes);
        //Add any nodes with mempool transactions in case these have been replaced
        Set<WalletNode> mempoolNodes = wallet.getWalletTxos().entrySet().stream()
                .filter(entry -> entry.getKey().getHeight() <= 0 || (entry.getKey().getSpentBy() != null && entry.getKey().getSpentBy().getHeight() <= 0))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
        historyNodes.addAll(mempoolNodes);

        subscribeWalletNodes(wallet, historyNodes, nodeTransactionMap, 0);
        getReferences(wallet, nodeTransactionMap.keySet(), nodeTransactionMap, 0);
        Set<BlockTransactionHash> newReferences = nodeTransactionMap.values().stream().flatMap(Collection::stream).filter(ref -> !wallet.getTransactions().containsKey(ref.getHash())).collect(Collectors.toSet());
        getReferencedTransactions(wallet, nodeTransactionMap);

        //Subscribe and retrieve transaction history from child nodes if necessary to maintain gap limit
        Set<KeyPurpose> keyPurposes = nodes.stream().map(WalletNode::getKeyPurpose).collect(Collectors.toUnmodifiableSet());
        for(KeyPurpose keyPurpose : keyPurposes) {
            WalletNode purposeNode = wallet.getNode(keyPurpose);
            getHistoryToGapLimit(wallet, nodeTransactionMap, purposeNode);
        }

        log.debug("Fetched nodes history for: " + nodeTransactionMap.keySet());

        if(!newReferences.isEmpty()) {
            //Look for additional nodes to fetch history for by considering the inputs and outputs of new transactions found
            log.debug(wallet.getFullName() + " found new transactions: " + newReferences);
            Set<WalletNode> additionalNodes = new HashSet<>();
            Map<String, WalletNode> walletScriptHashes = getAllScriptHashes(wallet);
            for(BlockTransactionHash reference : newReferences) {
                BlockTransaction blockTransaction = wallet.getTransactions().get(reference.getHash());
                for(TransactionOutput txOutput : blockTransaction.getTransaction().getOutputs()) {
                    WalletNode node = walletScriptHashes.get(getScriptHash(txOutput));
                    if(node != null && !historyNodes.contains(node)) {
                        additionalNodes.add(node);
                    }
                }

                for(TransactionInput txInput : blockTransaction.getTransaction().getInputs()) {
                    BlockTransaction inputBlockTransaction = wallet.getTransactions().get(txInput.getOutpoint().getHash());
                    if(inputBlockTransaction != null) {
                        TransactionOutput txOutput = inputBlockTransaction.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
                        WalletNode node = walletScriptHashes.get(getScriptHash(txOutput));
                        if(node != null && !historyNodes.contains(node)) {
                            additionalNodes.add(node);
                        }
                    }
                }
            }

            if(!additionalNodes.isEmpty()) {
                log.debug("Found additional nodes: " + additionalNodes);
                subscribeWalletNodes(wallet, additionalNodes, nodeTransactionMap, 0);
                getReferences(wallet, additionalNodes, nodeTransactionMap, 0);
                getReferencedTransactions(wallet, nodeTransactionMap);
            }
        }

        return nodeTransactionMap;
    }

    public void getHistory(Wallet wallet, KeyPurpose keyPurpose, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        WalletNode purposeNode = wallet.getNode(keyPurpose);
        //Subscribe to all existing address WalletNodes and add them to nodeTransactionMap as keys to empty sets if they have history that needs to be fetched
        subscribeWalletNodes(wallet, getAddressNodes(wallet, purposeNode), nodeTransactionMap, 0);
        //All WalletNode keys in nodeTransactionMap need to have their history fetched (nodes without history will not be keys in the map yet)
        getReferences(wallet, nodeTransactionMap.keySet(), nodeTransactionMap, 0);
        //Fetch all referenced transaction to wallet transactions map. We do this now even though it is done again later to get it done before too many script hashes are subscribed
        getReferencedTransactions(wallet, nodeTransactionMap);
        //Increase child nodes if necessary to maintain gap limit, and ensure they are subscribed and history is fetched
        getHistoryToGapLimit(wallet, nodeTransactionMap, purposeNode);

        log.debug("Fetched history for: " + nodeTransactionMap.keySet());

        //Set the remaining WalletNode keys in nodeTransactionMap to empty sets to indicate no history (if no script hash history has already been retrieved in a previous call)
        getAddressNodes(wallet, purposeNode).stream().filter(node -> !nodeTransactionMap.containsKey(node) && retrievedScriptHashes.get(getScriptHash(node)) == null).forEach(node -> nodeTransactionMap.put(node, Collections.emptySet()));
    }

    private void getHistoryToGapLimit(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, WalletNode purposeNode) throws ServerException {
        //Because node children are added sequentially in WalletNode.fillToIndex, we can simply look at the number of children to determine the highest filled index
        int historySize = purposeNode.getChildren().size();
        //The gap limit size takes the highest used index in the retrieved history and adds the gap limit (plus one to be comparable to the number of children since index is zero based)
        int gapLimitSize = getGapLimitSize(wallet, nodeTransactionMap, purposeNode);
        while(historySize < gapLimitSize) {
            purposeNode.fillToIndex(wallet, gapLimitSize - 1);
            subscribeWalletNodes(wallet, getAddressNodes(wallet, purposeNode), nodeTransactionMap, historySize);
            getReferences(wallet, nodeTransactionMap.keySet(), nodeTransactionMap, historySize);
            getReferencedTransactions(wallet, nodeTransactionMap);
            historySize = purposeNode.getChildren().size();
            gapLimitSize = getGapLimitSize(wallet, nodeTransactionMap, purposeNode);
        }
    }

    private Set<WalletNode> getAddressNodes(Wallet wallet, WalletNode purposeNode) {
        Integer watchLast = wallet.getWatchLast();
        if(watchLast == null || watchLast < wallet.getGapLimit() || wallet.getStoredBlockHeight() == null || wallet.getStoredBlockHeight() == 0 || wallet.getTransactions().isEmpty()) {
            return purposeNode.getChildren();
        }

        int highestUsedIndex = purposeNode.getChildren().stream().filter(WalletNode::isUsed).mapToInt(WalletNode::getIndex).max().orElse(0);
        int startFromIndex = highestUsedIndex - watchLast;
        return purposeNode.getChildren().stream().filter(walletNode -> walletNode.getIndex() >= startFromIndex).collect(Collectors.toCollection(TreeSet::new));
    }

    private int getGapLimitSize(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, WalletNode purposeNode) {
        int highestIndex = nodeTransactionMap.keySet().stream().filter(node -> node.getDerivation().size() > 1 && purposeNode.getKeyPurpose() == node.getKeyPurpose())
                .map(WalletNode::getIndex).max(Comparator.comparing(Integer::valueOf)).orElse(-1);
        return highestIndex + wallet.getGapLimit() + 1;
    }

    public void getReferences(Wallet wallet, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, int startIndex) throws ServerException {
        try {
            Map<WalletNode, ScriptHashTx[]> nodeHashHistory = new LinkedHashMap<>(nodes.size());
            Map<String, String> pathScriptHashes = new LinkedHashMap<>(nodes.size());
            for(WalletNode node : nodes) {
                if(node.getIndex() >= startIndex) {
                    pathScriptHashes.put(node.getDerivationPath(), getScriptHash(node));
                    nodeHashHistory.put(node, null);
                }
            }

            if(pathScriptHashes.isEmpty()) {
                return;
            }

            //Optimistic optimizations from guessing the script hash status based on known information
            Map<Sha256Hash, BlockTransaction> candidateTxs = new LinkedHashMap<>(broadcastedTransactions);
            wallet.getTransactions().forEach((txid, blkTx) -> {
                if(blkTx.getHeight() <= 0) {
                    candidateTxs.putIfAbsent(txid, blkTx);
                }
            });

            //Precompute the predicted height per candidate by inspecting in-wallet parents
            Map<Sha256Hash, Integer> candidateHeights = new HashMap<>(candidateTxs.size());
            for(Map.Entry<Sha256Hash, BlockTransaction> e : candidateTxs.entrySet()) {
                int predicted = 0;
                for(TransactionInput input : e.getValue().getTransaction().getInputs()) {
                    BlockTransaction parent = wallet.getWalletTransaction(input.getOutpoint().getHash());
                    if(parent != null && parent.getHeight() <= 0) {
                        predicted = -1;
                        break;
                    }
                }
                candidateHeights.put(e.getKey(), predicted);
            }

            for(Map.Entry<WalletNode, ScriptHashTx[]> entry : nodeHashHistory.entrySet()) {
                WalletNode node = entry.getKey();
                String scriptHash = pathScriptHashes.get(node.getDerivationPath());
                List<String> statuses = subscribedScriptHashes.get(scriptHash);

                if(statuses != null && !statuses.isEmpty()) {
                    //Optimize for txs that are already known (broadcasted or mempool-persisted)
                    for(Sha256Hash txid : candidateTxs.keySet()) {
                        BlockTransaction blkTx = candidateTxs.get(txid);
                        if(blkTx.getTransaction().getOutputs().stream().map(ElectrumServer::getScriptHash).anyMatch(scriptHash::equals) ||
                            blkTx.getTransaction().getInputs().stream().map(txInput -> getPrevOutput(wallet, txInput))
                                    .filter(Objects::nonNull).map(ElectrumServer::getScriptHash).anyMatch(scriptHash::equals)) {
                            List<ScriptHashTx> scriptHashTxes = new ArrayList<>(getScriptHashes(scriptHash, node));
                            scriptHashTxes.add(new ScriptHashTx(candidateHeights.get(txid), txid.toString(), blkTx.getFee() == null ? 0 : blkTx.getFee()));

                            String status = getScriptHashStatus(scriptHashTxes);
                            if(Objects.equals(status, statuses.getLast())) {
                                entry.setValue(scriptHashTxes.toArray(new ScriptHashTx[0]));
                                pathScriptHashes.remove(node.getDerivationPath());
                            }
                        }
                    }

                    //Optimize for new confirmations should all pending transactions confirm at the current block height
                    if(entry.getValue() == null && AppServices.getCurrentBlockHeight() != null &&
                            node.getTransactionOutputs().stream().flatMap(txo -> txo.isSpent() ? Stream.of(txo, txo.getSpentBy()) : Stream.of(txo))
                                    .anyMatch(txo -> txo.getHeight() <= 0)) {
                        List<ScriptHashTx> scriptHashTxes = getScriptHashes(scriptHash, node);
                        for(ScriptHashTx scriptHashTx : scriptHashTxes) {
                            if(scriptHashTx.height <= 0) {
                                scriptHashTx.height = AppServices.getCurrentBlockHeight();
                                scriptHashTx.fee = 0;
                            }
                        }

                        String status = getScriptHashStatus(scriptHashTxes);
                        if(Objects.equals(status, statuses.getLast())) {
                            entry.setValue(scriptHashTxes.toArray(new ScriptHashTx[0]));
                            pathScriptHashes.remove(node.getDerivationPath());
                        }
                    }
                }
            }

            if(!pathScriptHashes.isEmpty()) {
                //Even if we have some successes, failure to retrieve all references will result in an incomplete wallet history. Don't proceed if that's the case.
                Map<String, ScriptHashTx[]> result = electrumServerRpc.getScriptHashHistory(getTransport(), wallet, pathScriptHashes, true);

                for(String path : result.keySet()) {
                    ScriptHashTx[] txes = result.get(path);

                    Optional<WalletNode> optionalNode = nodes.stream().filter(n -> n.getDerivationPath().equals(path)).findFirst();
                    if(optionalNode.isPresent()) {
                        WalletNode node = optionalNode.get();
                        nodeHashHistory.put(node, txes);
                    }
                }
            }

            for(WalletNode node : nodeHashHistory.keySet()) {
                ScriptHashTx[] txes = nodeHashHistory.get(node);

                //Some servers can return the same tx as multiple ScriptHashTx entries with different heights. Take the highest height only
                Set<BlockTransactionHash> references = Arrays.stream(txes).map(ScriptHashTx::getBlockchainTransactionHash)
                        .collect(TreeSet::new, (set, ref) -> {
                            Optional<BlockTransactionHash> optExisting = set.stream().filter(prev -> prev.getHash().equals(ref.getHash())).findFirst();
                            if(optExisting.isPresent()) {
                                if(optExisting.get().getHeight() < ref.getHeight()) {
                                    set.remove(optExisting.get());
                                    set.add(ref);
                                }
                            } else {
                                set.add(ref);
                            }
                        }, TreeSet::addAll);
                Set<BlockTransactionHash> existingReferences = nodeTransactionMap.get(node);

                if(existingReferences == null) {
                    nodeTransactionMap.put(node, references);
                } else {
                    for(BlockTransactionHash reference : references) {
                        if(!existingReferences.add(reference)) {
                            Optional<BlockTransactionHash> optionalReference = existingReferences.stream().filter(tr -> tr.getHash().equals(reference.getHash())).findFirst();
                            if(optionalReference.isPresent()) {
                                BlockTransactionHash existingReference = optionalReference.get();
                                if(existingReference.getHeight() < reference.getHeight()) {
                                    existingReferences.remove(existingReference);
                                    existingReferences.add(reference);
                                }
                            }
                        }
                    }
                }
            }
        } catch (ElectrumServerRpcException e) {
            throw new ServerException(e.getMessage(), e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public void subscribeWalletNodes(Wallet wallet, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, int startIndex) throws ServerException {
        try {
            Set<String> scriptHashes = new HashSet<>();
            Map<String, String> pathScriptHashes = new LinkedHashMap<>();
            Map<String, WalletNode> pathNodes = new HashMap<>();
            for(WalletNode node : nodes) {
                if(node == null) {
                    log.error("Null node for wallet " + wallet.getFullName() + " subscribing nodes " + nodes + " startIndex " + startIndex, new Throwable());
                }

                if(node != null && node.getIndex() >= startIndex) {
                    String scriptHash = getScriptHash(node);
                    String subscribedStatus = getSubscribedScriptHashStatus(scriptHash);
                    if(subscribedStatus != null) {
                        //Already subscribed, but still need to fetch history from a used node if not previously fetched or present
                        if(!subscribedStatus.equals(retrievedScriptHashes.get(scriptHash)) || !subscribedStatus.equals(getScriptHashStatus(scriptHash, node))) {
                            nodeTransactionMap.put(node, new TreeSet<>());
                        }
                    } else if(!subscribedScriptHashes.containsKey(scriptHash) && scriptHashes.add(scriptHash)) {
                        //Unique script hash we are not yet subscribed to
                        pathScriptHashes.put(node.getDerivationPath(), scriptHash);
                        pathNodes.put(node.getDerivationPath(), node);
                    }
                }
            }

            log.debug("Subscribe to:        " + pathScriptHashes.keySet());

            if(pathScriptHashes.isEmpty()) {
                return;
            }

            Map<String, String> result = electrumServerRpc.subscribeScriptHashes(getTransport(), wallet, pathScriptHashes);

            for(String path : result.keySet()) {
                String status = result.get(path);

                WalletNode node = pathNodes.computeIfAbsent(path, p -> nodes.stream().filter(n -> n.getDerivationPath().equals(p)).findFirst().orElse(null));
                if(node != null) {
                    String scriptHash = getScriptHash(node);

                    //Check if there is history for this script hash, and if the history has changed since last fetched. The comparison against the
                    //node's own calculated status is what the already subscribed branch makes: subscriptions drop on every connect while retrieved
                    //statuses survive, so a node whose outputs disagree with the server would otherwise sit unfetched until its status changed
                    if(status != null && (!status.equals(retrievedScriptHashes.get(scriptHash)) || !status.equals(getScriptHashStatus(scriptHash, node)))) {
                        //Set the value for this node to be an empty set to mark it as requiring a get_history RPC call for this wallet
                        nodeTransactionMap.put(node, new TreeSet<>());
                    }

                    updateSubscribedScriptHashStatus(scriptHash, status);
                }
            }
        } catch (ElectrumServerRpcException e) {
            throw new ServerException(e.getMessage(), e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public List<Set<BlockTransactionHash>> getOutputTransactionReferences(Transaction transaction, int indexStart, int indexEnd, List<Set<BlockTransactionHash>> blockTransactionHashes) throws ServerException {
        try {
            Map<String, String> pathScriptHashes = new LinkedHashMap<>();
            for(int i = indexStart; i < transaction.getOutputs().size() && i < indexEnd; i++) {
                if(blockTransactionHashes.get(i) == null) {
                    TransactionOutput output = transaction.getOutputs().get(i);
                    pathScriptHashes.put(Integer.toString(i), getScriptHash(output));
                }
            }

            Map<String, ScriptHashTx[]> result = new HashMap<>();
            if(!pathScriptHashes.isEmpty()) {
                result = electrumServerRpc.getScriptHashHistory(getTransport(), null, pathScriptHashes, false);
            }

            for(String index : result.keySet()) {
                ScriptHashTx[] txes = result.get(index);

                int txBlockHeight = 0;
                Optional<BlockTransactionHash> optionalTxHash = Arrays.stream(txes)
                        .map(ScriptHashTx::getBlockchainTransactionHash)
                        .filter(ref -> ref.getHash().equals(transaction.getTxId()))
                        .findFirst();
                if(optionalTxHash.isPresent()) {
                    txBlockHeight = optionalTxHash.get().getHeight();
                }

                final int minBlockHeight = txBlockHeight;
                Set<BlockTransactionHash> references = Arrays.stream(txes)
                        .map(ScriptHashTx::getBlockchainTransactionHash)
                        .filter(ref -> !ref.getHash().equals(transaction.getTxId()) && ref.getHeight() >= minBlockHeight)
                        .collect(Collectors.toCollection(TreeSet::new));

                blockTransactionHashes.set(Integer.parseInt(index), references);
            }

            return blockTransactionHashes;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (ElectrumServerRpcException e) {
            throw new ServerException(e.getMessage(), e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public void getReferencedTransactions(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        //The write boundary for every caller: the references below reach the wallet transactions here and the node transaction outputs through
        //calculateNodeHistory, which consumes this same map after the unproven heights in it have been demoted in place
        Map<BlockTransactionHash, BlockHeader> proven = isVerifyingTransactions() ? verifyConfirmedReferences(wallet, nodeTransactionMap) : Collections.emptyMap();

        Map<BlockTransactionHash, Transaction> references = new TreeMap<>();
        for(Set<BlockTransactionHash> nodeReferences : nodeTransactionMap.values()) {
            for(BlockTransactionHash nodeReference : nodeReferences) {
                references.put(nodeReference, null);
            }
        }

        for(Iterator<Map.Entry<BlockTransactionHash, Transaction>> iter = references.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<BlockTransactionHash, Transaction> entry = iter.next();
            BlockTransactionHash reference = entry.getKey();
            BlockTransaction blockTransaction = wallet.getWalletTransaction(reference.getHash());
            if(blockTransaction != null) {
                //A reference proven this pass is not removed even where its height is unchanged: the only way it was proven at an unchanged height is that
                //its stored block was orphaned, so it must be rebuilt to carry the block it is now proven against. Its transaction comes from the wallet below,
                //so nothing is fetched for it
                if(reference.getHeight() == blockTransaction.getHeight() && (reference.getFee() == null || blockTransaction.getFee() != null)
                        && !proven.containsKey(reference)) {
                    iter.remove();
                } else {
                    entry.setValue(blockTransaction.getTransaction());
                }
            } else if(broadcastedTransactions.containsKey(reference.getHash())) {
                entry.setValue(broadcastedTransactions.get(reference.getHash()).getTransaction());
            }
        }

        Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
        if(!references.isEmpty()) {
            Map<Integer, BlockHeader> blockHeaderMap = getBlockHeaders(wallet, references.keySet());
            transactionMap = getTransactions(wallet, references, blockHeaderMap, proven);
        }

        //A re-proof at an unchanged height leaves the map equal to what the wallet holds, since a block hash is not part of that comparison
        if(!transactionMap.equals(wallet.getTransactions()) || !proven.isEmpty()) {
            wallet.updateTransactions(transactionMap);
            broadcastedTransactions.keySet().removeAll(transactionMap.entrySet().stream().filter(entry -> entry.getValue().getHeight() > 0)
                    .map(Map.Entry::getKey).collect(Collectors.toSet()));
        }
    }

    /**
     * Proves the confirmed references this pass introduces or changes, demoting in place those the server would not or could not prove, and returning
     * the exact (transaction, height) pairs proven with the verified headers they were proven against.
     * <p>
     * Everything here is keyed by the pair rather than by the transaction: a server can report one transaction at two heights on two script hashes,
     * and keyed by transaction alone the pair that did not prove would ride into the wallet on the back of the pair that did.
     */
    private Map<BlockTransactionHash, BlockHeader> verifyConfirmedReferences(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        Set<BlockTransactionHash> toProve = new LinkedHashSet<>();
        Set<BlockTransactionHash> refusedNow = new HashSet<>();
        for(Set<BlockTransactionHash> references : nodeTransactionMap.values()) {
            for(BlockTransactionHash reference : references) {
                if(reference.getHeight() > 0) {
                    if(refusedThisPass.contains(reference)) {
                        refusedNow.add(reference);      //already refused earlier in this pass, so demote it again without spending the retries again
                    } else {
                        BlockTransaction existing = wallet.getWalletTransaction(reference.getHash());
                        if(existing == null || existing.getHeight() != reference.getHeight() || isProvenAgainstOrphanedHeader(existing)) {
                            toProve.add(reference);
                        }
                    }
                }
            }
        }

        if(!refusedNow.isEmpty()) {
            demoteReferences(nodeTransactionMap, refusedNow);
        }

        if(toProve.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<BlockTransactionHash, BlockHeader> proven;
        try {
            proven = verifyMerkleProofs(wallet, toProve);
        } catch(UnsupportedMethodException e) {
            disableVerification(e);
            return Collections.emptyMap();      //nothing has been refused, so the history simply proceeds unverified
        } catch(ProofsUnavailableException e) {
            disableVerification(e);
            return Collections.emptyMap();
        }

        Set<BlockTransactionHash> unproven = new LinkedHashSet<>(toProve);
        unproven.removeAll(proven.keySet());
        if(!unproven.isEmpty()) {
            refusedThisPass.addAll(unproven);
            //Exactly these pairs: a reference for the same transaction at another height is untouched and is judged on its own proof
            demoteReferences(nodeTransactionMap, unproven);
        }

        return proven;
    }

    /**
     * Proves the confirmed heights a silent payments batch introduces, demoting to unconfirmed in place those the connected server would not prove.
     * These transactions are new to the wallet by construction, so unlike the history path there is nothing here already stored to compare against.
     */
    Map<BlockTransactionHash, BlockHeader> verifySilentPaymentReferences(Wallet wallet, Map<BlockTransactionHash, Transaction> referencesToFetch) throws ServerException {
        Set<BlockTransactionHash> toProve = referencesToFetch.keySet().stream().filter(reference -> reference.getHeight() > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if(toProve.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<BlockTransactionHash, BlockHeader> proven;
        try {
            proven = verifyMerkleProofs(wallet, toProve);
        } catch(UnsupportedMethodException e) {
            disableVerification(e);
            return Collections.emptyMap();
        } catch(ProofsUnavailableException e) {
            disableVerification(e);
            return Collections.emptyMap();
        }

        for(BlockTransactionHash reference : toProve) {
            if(!proven.containsKey(reference)) {
                Transaction transaction = referencesToFetch.remove(reference);
                referencesToFetch.put(new BlockTransaction(reference.getHash(), 0, null, reference.getFee(), null), transaction);
            }
        }

        return proven;
    }

    /**
     * Handles the connected server never answering a request for proofs at all, rather than failing to prove a particular transaction. Where
     * verification is mandatory the history fails and the public server rotates; elsewhere the session proceeds unverified, since denying a wallet its
     * history is worse than not verifying it against a server its owner chose. A lost connection arrives as the same exception and is not this, so the
     * transport is asked: with the connection gone it is the ordinary reconnect's business.
     */
    private static void disableVerification(ProofsUnavailableException e) throws ServerException {
        if(isVerificationMandatory() || !isConnected()) {
            throw e;
        }

        log.warn("Server could not supply transaction proofs, disabling transaction verification for this session: " + e.getMessage());
        serverCapability.withMerkleProofs(false);
    }

    /**
     * Handles a server that turns out not to implement a call verification needs, on the same terms: the state the capability mapping would have
     * produced had it known.
     */
    private static void disableVerification(UnsupportedMethodException e) throws ServerException {
        if(isVerificationMandatory()) {
            throw new ServerException("Server does not support transaction verification (" + e.getMethod() + ")", e);
        }

        log.warn("Server does not support " + e.getMethod() + ", disabling transaction verification for this session");
        serverCapability.withMerkleProofs(false);
    }

    /**
     * Replaces each of the given references with an unconfirmed one in every node that holds it, which is how an unproven height is kept out of the
     * wallet: it flows on through both sinks as an ordinary mempool transaction rather than through a path of its own.
     */
    private void demoteReferences(Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, Set<BlockTransactionHash> unproven) {
        for(Map.Entry<WalletNode, Set<BlockTransactionHash>> entry : nodeTransactionMap.entrySet()) {
            Set<BlockTransactionHash> references = entry.getValue();
            if(!references.isEmpty()) {
                for(BlockTransactionHash reference : unproven) {
                    if(references.remove(reference)) {
                        references.add(new BlockTransaction(reference.getHash(), 0, null, reference.getFee(), null));
                        //Recorded for the changed history check in fetchAndCalculateWalletHistory, which this node would otherwise trip
                        demotedScriptHashes.add(getScriptHash(entry.getKey()));
                    }
                }
            }
        }
    }

    /**
     * Whether a stored transaction is held at a height that is still numerically what the server reports but was proven against a block the chain no
     * longer holds, which is the form a reorg takes when a transaction is re-included at the same height. Above the last fork point this session only,
     * so an ordinary pass reads neither the store nor the disk.
     */
    private boolean isProvenAgainstOrphanedHeader(BlockTransaction existing) throws ServerException {
        if(existing.getBlockHash() == null || existing.getHeight() <= lastReorgForkHeight) {
            return false;       //history from before this feature, or a height no reorg this session has reached
        }

        try {
            BlockHeader stored = getHeaderStore().getHeader(existing.getHeight());
            return stored != null && !stored.getHash().equals(existing.getBlockHash());
        } catch(IOException e) {
            throw new ServerException("Could not read the block header store", e);
        }
    }

    /**
     * Verifies an inclusion proof for each given (transaction, height) pair, returning those proven with the header they were proven against. What is
     * missing was refused or shown false, told apart by what the server did rather than said: one unable to keep up fails whole batches and recovers
     * within the retries, while one that cannot substantiate a pair leaves it unanswered while its siblings succeed.
     */
    private Map<BlockTransactionHash, BlockHeader> verifyMerkleProofs(Wallet wallet, Set<BlockTransactionHash> toProve) throws ServerException {
        Map<String, BlockTransactionHash> outstanding = new LinkedHashMap<>();
        toProve.forEach(reference -> outstanding.put(reference.getHashAsString() + ":" + reference.getHeight(), reference));
        Map<BlockTransactionHash, BlockHeader> proven = new HashMap<>();
        //Accumulated here and merged out only on return. What is classified below is demoted by the caller on that return and on no other exit, so
        //writing it as we go would let an exception mid-loop report a finding without the demotion it describes - telling the user a transaction is
        //shown as unconfirmed while it is written confirmed
        Set<BlockTransactionHash> failedProofs = new LinkedHashSet<>();
        Set<BlockTransactionHash> refusedProofs = new LinkedHashSet<>();
        prefetchVerifiedHeaders(toProve.stream().map(BlockTransactionHash::getHeight).toList());

        ElectrumServerRpcException batchFailure = null;
        boolean answered = false;
        for(int attempt = 0; attempt < proofAttempts && !outstanding.isEmpty(); attempt++) {
            if(attempt > 0) {
                try {
                    //Jittered, so that many clients refused by one overloaded server do not retry in step
                    TimeUnit.MILLISECONDS.sleep(proofRetryDelayMillis + new Random().nextInt((int)proofRetryDelayMillis + 1));
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServerException("Interrupted while verifying transactions", e);     //the task was cancelled, so fail it rather than report refusals
                }
            }

            Map<String, TransactionMerkleProof> proofs;
            try {
                proofs = electrumServerRpc.getTransactionMerkleProofs(getTransport(), wallet, outstanding.values());
                batchFailure = null;
                answered = true;
            } catch(UnsupportedMethodException e) {
                throw e;    //before the catch below: whether the server implements the call is settled, and no retry will change it
            } catch(ElectrumServerRpcException e) {
                //The whole call failing says nothing about any one transaction, and a server momentarily unable to answer looks exactly like one
                //that never will until the retries have been spent. Left to the loop, which is what tells the two apart
                batchFailure = e;
                continue;
            }

            for(Map.Entry<String, TransactionMerkleProof> entry : proofs.entrySet()) {
                BlockTransactionHash reference = outstanding.get(entry.getKey());
                TransactionMerkleProof proof = entry.getValue();
                //An error, or a proof for some other block, leaves the pair outstanding: the server has not substantiated the height it reported
                if(reference != null && proof != TransactionMerkleProof.ERROR_PROOF && proof.block_height == reference.getHeight()) {
                    outstanding.remove(entry.getKey());
                    BlockHeader header = getVerifiedHeader(reference.getHeight());
                    if(header == null) {
                        refusedProofs.add(reference);       //the height itself cannot be substantiated
                    } else if(verifyProof(reference.getHash(), proof, header)) {
                        proven.put(reference, header);
                    } else {
                        failedProofs.add(reference);        //the branch does not reconstruct against a verified header
                    }
                }
            }
        }

        if(batchFailure != null) {
            //Every attempt failed as a whole rather than per transaction, so nothing here has been refused. Never answered at all is a server unable
            //to serve the call, which the callers may work around; answered and then not is one that can, so it fails the pass like any other
            if(answered) {
                throw new ServerException(batchFailure.getMessage(), batchFailure.getCause());
            }

            throw new ProofsUnavailableException(batchFailure.getMessage(), batchFailure.getCause());
        }

        //Reported at this height, then not substantiated at it through every attempt
        refusedProofs.addAll(outstanding.values());

        if(!failedProofs.isEmpty()) {
            failed.computeIfAbsent(wallet, w -> new LinkedHashSet<>()).addAll(failedProofs);
        }
        if(!refusedProofs.isEmpty()) {
            refused.computeIfAbsent(wallet, w -> new LinkedHashSet<>()).addAll(refusedProofs);
        }

        return proven;
    }

    /**
     * Whether the branch reconstructs the merkle root of the given verified header from the given transaction. Any malformed proof is a proof that
     * does not verify rather than a broken session.
     */
    static boolean verifyProof(Sha256Hash txid, TransactionMerkleProof proof, BlockHeader header) {
        if(proof.merkle == null || proof.merkle.size() > MerkleBranch.MAX_DEPTH) {
            return false;
        }

        try {
            MerkleBranch branch = new MerkleBranch(proof.pos, proof.merkle.stream().map(Sha256Hash::wrap).toList());
            return branch.computeRoot(txid).equals(header.getMerkleRoot());
        } catch(RuntimeException e) {
            log.warn("Invalid merkle proof for " + txid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Fetches and verifies, in batched pages, the header ranges below the last pin the given heights need, so getVerifiedHeader serves them from the
     * session cache. The one header path worth batching: a range averages a difficulty period, and a restore can touch dozens while the user waits.
     */
    void prefetchVerifiedHeaders(Collection<Integer> heights) throws ServerException {
        HeaderCheckpoints checkpoints = Network.get().getHeaderCheckpoints();
        Set<Integer> subCheckpointHeights = new TreeSet<>();
        for(int height : heights) {
            if(height > 0 && height <= checkpoints.getMaxHeight() && !verifiedHistoricalHeaders.containsKey(height)) {
                subCheckpointHeights.add(height);
            }
        }

        if(subCheckpointHeights.isEmpty()) {
            return;     //every height is above the last pin, and is served by the store the forward sync maintains
        }

        //Under the same lock as the forward sync and the single range fetch, so that two wallets restoring over the same periods fetch each once
        synchronized(headerSyncLock) {
            Map<Integer, Integer> ranges = new TreeMap<>();
            for(int height : subCheckpointHeights) {
                //A height already verified needs nothing, and one an added range covers is fetched by that range. Ranges reach up to the nearest
                //verified header, so one usually covers a period - but not where part of it was verified already, hence asking rather than assuming
                if(!verifiedHistoricalHeaders.containsKey(height) && ranges.entrySet().stream()
                        .noneMatch(range -> range.getKey() <= height && height < range.getKey() + range.getValue())) {
                    ranges.put(height, getVerifiedAnchorHeight(height, checkpoints.getPinnedHeightAtOrAbove(height)) - height + 1);
                }
            }

            if(ranges.isEmpty()) {
                return;
            }

            Map<Integer, BlockHeaders> chunks;
            try {
                chunks = electrumServerRpc.getBlockHeadersChunks(getTransport(), ranges);
            } catch(UnsupportedMethodException e) {
                throw e;
            } catch(ElectrumServerRpcException e) {
                throw new ServerException(e.getMessage(), e.getCause());
            }

            //A range the server errored on or answered malformed is absent, and one that fails linkage is not cached: either way getVerifiedHeader
            //fetches it singly, and its heights resolve to refusals in the ordinary way if that fails too
            for(Map.Entry<Integer, BlockHeaders> chunk : chunks.entrySet()) {
                verifyAndCacheRange(chunk.getKey(), ranges.get(chunk.getKey()), chunk.getValue());
            }
        }
    }

    /**
     * Raises one dialog per wallet for what this task could not prove, from a finally so that a later failure in the pass cannot bury a finding whose
     * demotion has already been written. Each pair is reported once per session, since the passes that follow a refusal re-attempt it.
     */
    void postProofEvents(Wallet wallet) {
        postProofEvents(wallet, wallet);
    }

    void postProofEvents(Wallet wallet, Wallet reportWallet) {
        //Logged before the warned set is consulted, so that the log records a finding on every pass it recurs even where its dialog has been shown
        //once already. Without it a refusal that is filtered leaves no trace at all, and cannot be told from no proof having been asked for
        Set<BlockTransactionHash> walletFailed = failed.remove(wallet);
        if(walletFailed != null && !walletFailed.isEmpty()) {
            log.warn("Inclusion proofs from the connected server did not reconstruct the block they were supplied for: " + describePairs(walletFailed));
            Set<BlockTransactionHash> unwarnedFailed = filterWarned(walletFailed, true);
            if(!unwarnedFailed.isEmpty()) {
                EventManager.get().post(new TransactionProofsFailedEvent(reportWallet, unwarnedFailed));
            }
        }

        Set<BlockTransactionHash> walletRefused = refused.remove(wallet);
        if(walletRefused != null && !walletRefused.isEmpty()) {
            log.warn("The connected server would not prove the heights it reported for: " + describePairs(walletRefused));
            Set<BlockTransactionHash> unwarnedRefused = filterWarned(walletRefused, false);
            if(!unwarnedRefused.isEmpty()) {
                EventManager.get().post(new TransactionProofsRefusedEvent(reportWallet, unwarnedRefused));
            }
        }
    }

    private static String describePairs(Set<BlockTransactionHash> references) {
        return references.stream().map(reference -> reference.getHashAsString() + ":" + reference.getHeight()).collect(Collectors.joining(", "));
    }

    /**
     * The findings not yet shown to the user, recording what it returns as shown. A pair shown false is raised even where it was refused on an earlier
     * pass, the two being different claims; a refusal of a pair already shown false is not, saying less than what has been said. Within one pass the
     * order of the two posts above already gets this right.
     */
    private static Set<BlockTransactionHash> filterWarned(Set<BlockTransactionHash> references, boolean shownFalse) {
        return references.stream().filter(reference -> {
            String pair = reference.getHashAsString() + ":" + reference.getHeight();
            if(shownFalse) {
                proofWarnedPairs.add(pair);
                return proofsShownFalseWarnedPairs.add(pair);
            }

            return proofWarnedPairs.add(pair);
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Map<Integer, BlockHeader> getBlockHeaders(Wallet wallet, Set<BlockTransactionHash> references) throws ServerException {
        try {
            Map<Integer, BlockHeader> blockHeaderMap = new TreeMap<>();
            Set<Integer> blockHeights = new TreeSet<>();
            for(BlockTransactionHash reference : references) {
                if(reference.getHeight() > 0) {
                    blockHeights.add(reference.getHeight());
                }
            }

            //Every height proven this pass already has its verified header in the store or the session cache, so serving timestamps from there is
            //what leaves a confirmed wallet transaction emitting the same single call a recent transaction's confirmation does. Asked before
            //retrievedBlockHeaders, and not copied into it: that cache holds every announced tip and is never rewound, so after a reorg it names
            //the replaced block at any height that was one, while a header read from the store cannot be stale that way
            putVerifiedHeaders(blockHeaderMap, blockHeights);

            for(Iterator<Integer> iter = blockHeights.iterator(); iter.hasNext(); ) {
                Integer blockHeight = iter.next();
                BlockHeader retrievedHeader = retrievedBlockHeaders.get(blockHeight);
                if(retrievedHeader != null) {
                    blockHeaderMap.put(blockHeight, retrievedHeader);
                    iter.remove();
                }
            }

            if(blockHeights.isEmpty()) {
                return blockHeaderMap;
            }

            Map<Integer, String> result = electrumServerRpc.getBlockHeaders(getTransport(), wallet, blockHeights);

            for(Integer height : result.keySet()) {
                byte[] blockHeaderBytes = Utils.hexToBytes(result.get(height));
                BlockHeader blockHeader = new BlockHeader(blockHeaderBytes);
                blockHeaderMap.put(height, blockHeader);
                updateRetrievedBlockHeaders(height, blockHeader);
                blockHeights.remove(height);
            }

            if(!blockHeights.isEmpty()) {
                log.warn("Could not retrieve " + blockHeights.size() + " blocks");
            }

            return blockHeaderMap;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (ElectrumServerRpcException e) {
            throw new ServerException(e.getMessage(), e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    /**
     * Serves what it can of the given heights from the store and from the session cache of headers linked to a pin, removing those it serves from the
     * set of heights left to fetch. Nothing here fetches anything: a viewer looking up a date must not set a header range downloading, so the store is
     * read only where it is already loaded and already covers the height, and a store that cannot be read is simply not used.
     */
    private static void putVerifiedHeaders(Map<Integer, BlockHeader> blockHeaderMap, Set<Integer> blockHeights) {
        try {
            HeaderStore store = headerStore;
            HeaderStore readable = store != null && store.isIntact() ? store : null;
            for(Iterator<Integer> iter = blockHeights.iterator(); iter.hasNext(); ) {
                int height = iter.next();
                BlockHeader header = verifiedHistoricalHeaders.get(height);
                if(header == null && readable != null && height >= readable.getStartHeight() && height <= readable.getTipHeight()) {
                    header = readable.getHeader(height);
                }

                if(header != null) {
                    blockHeaderMap.put(height, header);
                    iter.remove();
                }
            }
        } catch(IOException e) {
            log.warn("Could not read the block header store: " + e.getMessage());
        }
    }

    /**
     * The header store for this network, loaded on first use from a background thread. It is not cleared when the server changes: headers are claims
     * about the chain rather than about the server, and a new server announcing a different tip is handled as an ordinary reorg.
     */
    static HeaderStore getHeaderStore() throws ServerException {
        try {
            HeaderStore store = headerStore;
            if(store != null && store.isIntact()) {
                return store;
            }

            synchronized(headerStoreLock) {
                //A loaded store outlives the connection, so its file may have been removed or truncated underneath it since it was read
                if(headerStore == null || !headerStore.isIntact()) {
                    headerStore = HeaderStore.load(Network.get().getHeaderCheckpoints());
                }

                return headerStore;
            }
        } catch(IOException e) {
            throw new ServerException("Could not load the block header store", e);
        }
    }

    /**
     * Advances the store to the tip the server has announced, taken as one value so that a height and a header from either side of a new block cannot
     * be mixed. Every chain problem - a linkage or difficulty failure, a short, empty or malformed chunk, or a reorg candidate that cannot
     * be accepted - is thrown as a VerificationException, meaning this server cannot substantiate these heights; transport problems propagate as they
     * are, meaning the session is broken.
     */
    void syncHeaders(ChainTip tip) throws ServerException {
        synchronized(headerSyncLock) {
            HeaderStore store = getHeaderStore();
            try {
                if(tip == null || tip.height() < store.getStartHeight()) {
                    return;     //no tip yet, or a server that has not caught up to the last pinned header
                }

                if(tip.height() <= store.getTipHeight() && !tip.header().getHash().equals(store.getHash(tip.height()))) {
                    reconcile(store, tip.height());     //the tie form of a divergence, which the loop below would never examine
                }

                syncTo(store, tip.height(), tip);
            } catch(IOException e) {
                throw new ServerException("Could not write to the block header store", e);
            }
        }
    }

    /**
     * Advances the store to the given height on the calling thread, for a wallet history that has reached a height the sync service has not, under the
     * exception contract of syncHeaders above. Both are internal to the header sync: it is getVerifiedHeader that turns what they throw into the
     * refusal, failure and unsupported outcomes its callers act on.
     */
    void syncHeadersTo(int height) throws ServerException {
        synchronized(headerSyncLock) {
            HeaderStore store = getHeaderStore();
            try {
                syncTo(store, height, AppServices.getAnnouncedTip());
            } catch(IOException e) {
                throw new ServerException("Could not write to the block header store", e);
            }
        }
    }

    private void syncTo(HeaderStore store, int targetHeight, ChainTip tip) throws ServerException, IOException {
        while(store.getTipHeight() < targetHeight) {
            if(tip != null && tip.height() == store.getTipHeight() + 1 && tip.header().getPrevBlockHash().equals(store.getTipHash())) {
                store.append(tip.header());     //the steady state: the announced header extends the store, so there is nothing to fetch
                continue;
            }

            int startHeight = store.getTipHeight() + 1;
            BlockHeaders chunk = electrumServerRpc.getBlockHeadersChunk(getTransport(), startHeight, HEADERS_CHUNK_SIZE);
            if(chunk.count == 0) {
                throw new VerificationException("Server returned no headers from height " + startHeight + " while the store must reach height " + targetHeight);
            }

            List<BlockHeader> headers;
            try {
                //Parsed whole here rather than one at a time below, so that a chunk carrying fewer headers than it claims is a refusal like any other
                //malformed response rather than an exception escaping the sync
                byte[] bytes = Utils.hexToBytes(chunk.hex);
                headers = IntStream.range(0, chunk.count).mapToObj(i -> new BlockHeader(bytes, i * HeaderStore.HEADER_LENGTH)).toList();
            } catch(ProtocolException | IllegalArgumentException e) {
                //Refusal class, as a short chunk is: the server could not substantiate the range
                throw new VerificationException("Server returned a malformed header chunk from height " + startHeight, e);
            }

            if(!headers.getFirst().getPrevBlockHash().equals(store.getTipHash())) {
                //The server's chain diverges from the store, so reconcile here, then re-read the tip and fetch again
                reconcile(store, targetHeight);
                continue;
            }

            store.append(headers);
        }
    }

    /**
     * Rewinds the store to the fork point it shares with the server's chain and adopts the server's headers above it, where they carry at least as much
     * work. Equal work is the same height tie of a stale block and must be adopted: a client with one server can only verify against the chain that
     * server serves, and staying on the replaced block would report every proof from the replacing one as dishonest.
     */
    private void reconcile(HeaderStore store, int tipHeight) throws ServerException, IOException {
        //The fork point is at or below the store tip whatever the server has announced, so the window sits there rather than at the announced tip: a
        //server hundreds of blocks ahead of a diverged store would otherwise be searched over a range holding no height the store has
        int endHeight = Math.min(tipHeight, store.getTipHeight() + 1);
        int startHeight = Math.max(endHeight - MAX_REORG_DEPTH + 1, store.getStartHeight());
        int count = endHeight - startHeight + 1;
        BlockHeaders chunk = electrumServerRpc.getBlockHeadersChunk(getTransport(), startHeight, count);
        if(chunk.count != count) {
            throw new VerificationException("Server returned " + chunk.count + " of " + count + " headers when reconciling to height " + endHeight);
        }

        List<BlockHeader> candidate;
        try {
            byte[] bytes = Utils.hexToBytes(chunk.hex);
            candidate = IntStream.range(0, count).mapToObj(i -> new BlockHeader(bytes, i * HeaderStore.HEADER_LENGTH)).toList();
        } catch(ProtocolException | IllegalArgumentException e) {
            throw new VerificationException("Server returned a malformed header chunk when reconciling to height " + endHeight, e);
        }

        //Walk back from the announced tip, checking each header against the one below it, until one descends from a header the store already holds
        int forkHeight = -1;
        for(int i = count - 1; i >= 0; i--) {
            if(candidate.get(i).getPrevBlockHash().equals(store.getHash(startHeight + i - 1))) {
                forkHeight = startHeight + i - 1;
                break;
            }
            if(i == 0 || !candidate.get(i).getPrevBlockHash().equals(candidate.get(i - 1).getHash())) {
                break;
            }
        }

        if(forkHeight < 0) {
            throw new VerificationException("Server's chain at height " + endHeight + " shares no fork point with the last " + count + " verified headers");
        }

        List<BlockHeader> segment = candidate.subList(forkHeight - startHeight + 1, count);
        HeaderChainState candidateState = store.chainStateAt(forkHeight);
        for(BlockHeader header : segment) {
            candidateState.add(header);
        }

        //Both chains measured from the same pinned anchor, so the work they share below the fork cancels and what remains is what would be adopted
        //against what would be discarded. In a forward sync the candidate reaches one header past the store tip, which is why it is heavier: the
        //server holding a header there is what exposed the divergence
        if(candidateState.getChainWork().compareTo(store.getChainWork()) < 0) {
            throw new VerificationException("Server's chain from height " + (forkHeight + 1) + " carries less work than the "
                    + (store.getTipHeight() - forkHeight) + " verified headers it would replace");
        }

        log.warn("Reorganising the block header store at height " + forkHeight + ", replacing " + (store.getTipHeight() - forkHeight) + " headers with " + segment.size());
        store.truncate(forkHeight);
        lastReorgForkHeight = Math.min(lastReorgForkHeight, forkHeight);
        //Every announced tip is cached there and nothing else rewinds it, so above the fork it would name the replaced block at any height that was
        //one - only the current tip is replaced by the next announcement. Dropped with the headers they came from
        int reorganisedFrom = forkHeight;
        retrievedBlockHeaders.keySet().removeIf(height -> height > reorganisedFrom);
        try {
            store.append(segment);
        } finally {
            //The truncation is what the wallets have to hear about, whether or not the replacement was written: a height above the fork was proven
            //against a header the store no longer holds either way. Dispatched on this thread, which is a wallet history thread as often as it is the
            //sync service: the wallet handler hops to the FX thread itself
            EventManager.get().post(new ChainReorgEvent(forkHeight));
        }
    }

    /**
     * The header at the given height verified against the compiled-in checkpoints, or null where the connected server cannot substantiate it, which is
     * reported as a refusal. Heights above the last pin are served from the store, and those below it by hash linkage to a pin.
     */
    public BlockHeader getVerifiedHeader(int height) throws ServerException {
        HeaderCheckpoints checkpoints = Network.get().getHeaderCheckpoints();
        if(height > checkpoints.getMaxHeight()) {
            HeaderStore store = getHeaderStore();
            try {
                //One object written once per event: the height and the header read separately can straddle a new block
                ChainTip announced = AppServices.getAnnouncedTip();
                if(announced != null && announced.height() >= store.getStartHeight() && announced.height() <= store.getTipHeight()
                        && !announced.header().getHash().equals(store.getHash(announced.height()))) {
                    //Never serve a stored header while the store tip disagrees with the announced tip: the tie form of a reorg
                    syncHeaders(announced);
                } else if(height > store.getTipHeight()) {
                    syncHeadersTo(height);      //the sync service has not caught up, so fetch on this thread
                }

                return store.getHeader(height);
            } catch(UnsupportedMethodException e) {
                throw e;    //before the catch below, so the caller can disable verification for the session rather than reading a refusal
            } catch(VerificationException e) {
                log.warn("Could not verify the header chain to height " + height + ": " + e.getMessage());
                return null;
            } catch(ElectrumServerRpcException e) {
                throw new ServerException(e.getMessage(), e.getCause());    //the server said nothing about this height, so it is a failed call rather than a refusal
            } catch(IOException e) {
                throw new ServerException("Could not read the block header store", e);
            }
        }

        if(height == 0) {
            return Network.get().getGenesisHeader();
        }

        BlockHeader cached = verifiedHistoricalHeaders.get(height);
        if(cached != null) {
            return cached;
        }

        //One call at a time crosses the transport, so this lock defers no fetch the connection would not have deferred anyway. What it buys is dedup: a
        //concurrent wallet wanting this range finds it cached, and one wanting an overlapping range fetches only the part below what is now cached.
        //Were the transport ever to carry calls concurrently, this would become the limiter and would want an in flight map keyed by range instead
        synchronized(headerSyncLock) {
            cached = verifiedHistoricalHeaders.get(height);
            if(cached != null) {
                return cached;
            }

            int count = getVerifiedAnchorHeight(height, checkpoints.getPinnedHeightAtOrAbove(height)) - height + 1;
            BlockHeaders chunk;
            try {
                chunk = electrumServerRpc.getBlockHeadersChunk(getTransport(), height, count);
            } catch(UnsupportedMethodException e) {
                throw e;    //before the catch below, so the caller can disable verification for the session rather than reading a refusal
            } catch(VerificationException e) {
                return null;    //a short or malformed response is refusal class here as in the forward sync, never a session failure
            } catch(ElectrumServerRpcException e) {
                throw new ServerException(e.getMessage(), e.getCause());
            }

            return verifyAndCacheRange(height, count, chunk);
        }
    }

    /**
     * The height of the header nearest above the given one that has already been verified this session, and the pinned height where there is none.
     * A header whose hash chain reaches a verified hash is that hash's ancestor at the corresponding depth, so linking to the nearest verified header
     * rather than always to the pin is what keeps a later pass over an already fetched period from downloading it again.
     */
    private static int getVerifiedAnchorHeight(int height, int pinnedHeight) {
        for(int above = height + 1; above < pinnedHeight; above++) {
            if(verifiedHistoricalHeaders.containsKey(above)) {
                return above;
            }
        }

        return pinnedHeight;
    }

    /**
     * Verifies a fetched range of headers below the last pin by hash linkage to its anchor and caches it for the session, returning the header the
     * range starts at, or null where the range is short, malformed or does not reach the anchor. Called with headerSyncLock held.
     */
    private static BlockHeader verifyAndCacheRange(int startHeight, int count, BlockHeaders chunk) {
        int anchorHeight = startHeight + count - 1;
        BlockHeader anchor = verifiedHistoricalHeaders.get(anchorHeight);
        Sha256Hash anchorHash = anchor != null ? anchor.getHash() : Network.get().getHeaderCheckpoints().getHash(anchorHeight);
        List<BlockHeader> headers = getLinkedHeaders(chunk, count, anchorHash);
        if(headers == null) {
            return null;
        }

        for(int i = 0; i < count; i++) {
            verifiedHistoricalHeaders.put(startHeight + i, headers.get(i));
        }

        return headers.getFirst();
    }

    /**
     * The headers of a requested range verified by hash linkage to the given anchor hash, which is the hash the last header of the range must have, or
     * null where the response is short or malformed or the chain does not reach the anchor. No proof of work, difficulty or timestamp check is needed
     * below a pinned header: descent from the pin is what places a header at its height.
     */
    static List<BlockHeader> getLinkedHeaders(BlockHeaders chunk, int count, Sha256Hash anchorHash) {
        if(chunk.count != count) {
            return null;
        }

        List<BlockHeader> headers;
        try {
            byte[] bytes = Utils.hexToBytes(chunk.hex);
            headers = IntStream.range(0, count).mapToObj(i -> new BlockHeader(bytes, i * HeaderStore.HEADER_LENGTH)).toList();
        } catch(ProtocolException | IllegalArgumentException e) {
            return null;
        }

        if(!headers.getLast().getHash().equals(anchorHash)) {
            return null;
        }

        for(int i = count - 2; i >= 0; i--) {
            if(!headers.get(i + 1).getPrevBlockHash().equals(headers.get(i).getHash())) {
                return null;
            }
        }

        return headers;
    }

    /**
     * Whether transactions are being verified against the connected server, which turns on the header sync and the inclusion proofs alike. The config
     * setting is asked here so one answer covers the sync, both write boundaries and the connect time enforcement.
     * <p>
     * A server below the last pinned header cannot substantiate any height, so asking would refuse every new confirmation and raise a dialog for it.
     * The public tier rejects such a server at connect; a private one still catching up simply goes unverified until it arrives. A tip not yet
     * announced is not evidence of lagging.
     * <p>
     * Not asked of a Bitcoin Core connection at all, whichever backend is fronting it: the node answering is the user's own, and a proof it built
     * against headers it also supplied establishes nothing it has not already been trusted for. Cormorant declares as much in its capability, but
     * bwt takes over where cormorant cannot start, and the same node should not verify or not according to which one did.
     */
    static boolean isVerifyingTransactions() {
        if(!Config.get().isVerifyTransactions() || Config.get().getServerType() == ServerType.BITCOIN_CORE
                || serverCapability == null || !serverCapability.supportsMerkleProofs()) {
            return false;
        }

        ChainTip announced = AppServices.getAnnouncedTip();
        return announced == null || announced.height() >= Network.get().getHeaderCheckpoints().getMaxHeight();
    }

    /**
     * Whether the connected server must support transaction verification to be used at all, which is the case for the public server tier on mainnet.
     * Turned off with verification itself, or a public server would still be rejected for lacking a call nothing is going to make.
     */
    static boolean isVerificationMandatory() {
        return Config.get().isVerifyTransactions() && Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER && Network.get() == Network.MAINNET;
    }

    public Map<Sha256Hash, BlockTransaction> getTransactions(Wallet wallet, Map<BlockTransactionHash, Transaction> references, Map<Integer, BlockHeader> blockHeaderMap) throws ServerException {
        return getTransactions(wallet, references, blockHeaderMap, Collections.emptyMap());
    }

    /**
     * Builds the wallet transactions the given references name, recording on each pair proven this pass the hash of the block it was proven against,
     * which is what identifies it later as held in a block the chain no longer has.
     */
    public Map<Sha256Hash, BlockTransaction> getTransactions(Wallet wallet, Map<BlockTransactionHash, Transaction> references, Map<Integer, BlockHeader> blockHeaderMap,
                                                             Map<BlockTransactionHash, BlockHeader> proven) throws ServerException {
        try {
            Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
            Set<BlockTransactionHash> checkReferences = new TreeSet<>(references.keySet());
            Set<Sha256Hash> provenTxids = new HashSet<>();

            Set<String> txids = new LinkedHashSet<>(references.size());
            for(BlockTransactionHash reference : references.keySet()) {
                if(references.get(reference) == null) {
                    txids.add(reference.getHashAsString());
                }
            }

            if(!txids.isEmpty()) {
                Map<String, String> result = electrumServerRpc.getTransactions(getTransport(), wallet, txids);

                String strErrorTx = Sha256Hash.ZERO_HASH.toString();
                for(String txid : result.keySet()) {
                    Sha256Hash hash = Sha256Hash.wrap(txid);
                    String strRawTx = result.get(txid);

                    if(strRawTx.equals(strErrorTx)) {
                        transactionMap.put(hash, UNFETCHABLE_BLOCK_TRANSACTION);
                        checkReferences.removeIf(ref -> ref.getHash().equals(hash));
                        continue;
                    }

                    Transaction transaction;

                    try {
                        transaction = new Transaction(Utils.hexToBytes(strRawTx));
                    } catch(Exception e) {
                        log.error("Could not parse tx: " + strRawTx, e);
                        continue;
                    }

                    if(!transaction.getTxId().equals(hash)) {
                        log.error("Server returned transaction " + transaction.getTxId() + " for requested txid " + hash);
                        throw new IllegalStateException("Server returned a transaction that does not match the requested txid " + hash);
                    }

                    //One transaction can be referenced at more than one height, and each of those references needs it: left without it, the second
                    //would be built as unfetchable and would overwrite the first in the map below, which is keyed by transaction alone
                    List<BlockTransactionHash> matching = references.keySet().stream().filter(reference -> reference.getHash().equals(hash)).toList();
                    if(matching.isEmpty()) {
                        throw new IllegalStateException("Returned transaction " + hash.toString() + " that was not requested");
                    }

                    matching.forEach(reference -> references.put(reference, transaction));
                }
            }

            for(BlockTransactionHash reference : references.keySet()) {
                Transaction transaction = references.get(reference);
                if(transaction == null) {
                    transactionMap.put(reference.getHash(), UNFETCHABLE_BLOCK_TRANSACTION);
                    checkReferences.removeIf(ref -> ref.getHash().equals(reference.getHash()));
                    continue;
                }

                Date blockDate = null;
                if(reference.getHeight() > 0) {
                    BlockHeader blockHeader = blockHeaderMap.get(reference.getHeight());
                    if(blockHeader == null) {
                        transactionMap.put(reference.getHash(), UNFETCHABLE_BLOCK_TRANSACTION);
                        checkReferences.removeIf(ref -> ref.getHash().equals(reference.getHash()));
                        continue;
                    }
                    blockDate = blockHeader.getTimeAsDate();
                }

                BlockTransaction cached = wallet == null ? null : wallet.getWalletTransaction(reference.getHash());
                Long fee = reference.getFee();
                if(fee == null && cached != null && cached.getFee() != null) {
                    fee = cached.getFee();
                }

                //An existing block hash is carried over only while the height it was proven at is unchanged, since a height that has changed has just
                //been proven against a different block or demoted to unconfirmed
                BlockHeader provenHeader = proven.get(reference);
                Sha256Hash blockHash = provenHeader != null ? provenHeader.getHash() :
                        (cached != null && cached.getHeight() == reference.getHeight() ? cached.getBlockHash() : null);
                BlockTransaction blockchainTransaction = new BlockTransaction(reference.getHash(), reference.getHeight(), blockDate, fee, transaction, blockHash);

                //Two references for one transaction collapse to one entry here, and the proven pair is the one that must survive: the other is a
                //height this server would not prove, which the demotion has already replaced with an unconfirmed reference
                if(provenHeader != null || !provenTxids.contains(reference.getHash())) {
                    transactionMap.put(reference.getHash(), blockchainTransaction);
                }
                if(provenHeader != null) {
                    provenTxids.add(reference.getHash());
                }
                checkReferences.remove(reference);
            }

            if(!checkReferences.isEmpty()) {
                throw new IllegalStateException("Could not retrieve transactions " + checkReferences);
            }

            return transactionMap;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getMessage(), e);
        } catch (ElectrumServerRpcException e) {
            throw new ServerException(e.getMessage(), e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public void calculateNodeHistory(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) {
        for(WalletNode node : nodeTransactionMap.keySet()) {
            calculateNodeHistory(wallet, nodeTransactionMap, node);
        }
    }

    public void calculateNodeHistory(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, WalletNode node) {
        Set<BlockTransactionHashIndex> transactionOutputs = new TreeSet<>();

        //First check all provided txes that pay to this node
        Script nodeScript = node.getOutputScript();
        Set<BlockTransactionHash> history = nodeTransactionMap.get(node);
        Map<Sha256Hash, BlockTransactionHash> txHashHistory = new HashMap<>();
        for(BlockTransactionHash reference : history) {
            txHashHistory.put(reference.getHash(), reference);
            BlockTransaction blockTransaction = wallet.getTransactions().get(reference.getHash());
            if(blockTransaction == null) {
                throw new IllegalStateException("Did not retrieve transaction for hash " + reference.getHashAsString());
            } else if(blockTransaction.equals(UNFETCHABLE_BLOCK_TRANSACTION)) {
                throw new IllegalStateException("Could not retrieve transaction for hash " + reference.getHashAsString());
            }
            Transaction transaction = blockTransaction.getTransaction();

            for(int outputIndex = 0; outputIndex < transaction.getOutputs().size(); outputIndex++) {
                TransactionOutput output = transaction.getOutputs().get(outputIndex);
                if (output.getScript().equals(nodeScript)) {
                    BlockTransactionHashIndex receivingTXO = new BlockTransactionHashIndex(reference.getHash(), reference.getHeight(), blockTransaction.getDate(), reference.getFee(), output.getIndex(), output.getValue());
                    transactionOutputs.add(receivingTXO);
                }
            }
        }

        //Then check all provided txes that pay from this node
        for(BlockTransactionHash reference : history) {
            BlockTransaction blockTransaction = wallet.getTransactions().get(reference.getHash());
            if(blockTransaction == null || blockTransaction.equals(UNFETCHABLE_BLOCK_TRANSACTION)) {
                throw new IllegalStateException("Could not retrieve transaction for hash " + reference.getHashAsString());
            }
            Transaction transaction = blockTransaction.getTransaction();

            for(int inputIndex = 0; inputIndex < transaction.getInputs().size(); inputIndex++) {
                TransactionInput input = transaction.getInputs().get(inputIndex);
                Sha256Hash previousHash = input.getOutpoint().getHash();
                BlockTransaction previousTransaction = wallet.getTransactions().get(previousHash);

                if(previousTransaction == null) {
                    //No referenced transaction found, cannot check if spends from wallet
                    //This is fine so long as all referenced transactions have been returned, in which case this refers to a transaction that does not affect this wallet
                    continue;
                } else if(previousTransaction.equals(UNFETCHABLE_BLOCK_TRANSACTION)) {
                    throw new IllegalStateException("Could not retrieve transaction for hash " + reference.getHashAsString());
                }

                BlockTransactionHash spentTxHash = txHashHistory.get(previousHash);
                if(spentTxHash == null) {
                    //No previous transaction history found, cannot check if spends from wallet
                    //This is fine so long as all referenced transactions have been returned, in which case this refers to a transaction that does not affect this wallet node
                    continue;
                }

                TransactionOutput spentOutput = previousTransaction.getTransaction().getOutputs().get((int)input.getOutpoint().getIndex());
                if(spentOutput.getScript().equals(nodeScript)) {
                    BlockTransactionHashIndex spendingTXI = new BlockTransactionHashIndex(reference.getHash(), reference.getHeight(), blockTransaction.getDate(), reference.getFee(), inputIndex, spentOutput.getValue());
                    BlockTransactionHashIndex spentTXO = new BlockTransactionHashIndex(spentTxHash.getHash(), spentTxHash.getHeight(), previousTransaction.getDate(), spentTxHash.getFee(), spentOutput.getIndex(), spentOutput.getValue(), spendingTXI);

                    Optional<BlockTransactionHashIndex> optionalReference = transactionOutputs.stream().filter(receivedTXO -> receivedTXO.getHash().equals(spentTXO.getHash()) && receivedTXO.getIndex() == spentTXO.getIndex()).findFirst();
                    if(optionalReference.isEmpty()) {
                        throw new IllegalStateException("Found spent transaction output " + spentTXO + " but no record of receiving it");
                    }

                    BlockTransactionHashIndex receivedTXO = optionalReference.get();
                    receivedTXO.setSpentBy(spendingTXI);
                }
            }
        }

        if(!transactionOutputs.equals(node.getTransactionOutputs())) {
            node.updateTransactionOutputs(wallet, transactionOutputs);
            copyPostmixLabels(wallet, transactionOutputs);
            copyBadbankLabels(wallet, transactionOutputs);
        }
    }

    public void copyPostmixLabels(Wallet wallet, Set<BlockTransactionHashIndex> newTransactionOutputs) {
        if(wallet.getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX && wallet.getMasterWallet() != null) {
            for(BlockTransactionHashIndex newRef : newTransactionOutputs) {
                BlockTransactionHashIndex prevRef = wallet.getWalletTxos().keySet().stream()
                        .filter(txo -> wallet.getMasterWallet().getUtxoMixData(txo) != null && txo.isSpent() && txo.getSpentBy().getHash().equals(newRef.getHash())).findFirst().orElse(null);
                if(prevRef != null && wallet.getMasterWallet().getUtxoMixData(newRef) != null) {
                    if(newRef.getLabel() == null && prevRef.getLabel() != null) {
                        newRef.setLabel(prevRef.getLabel());
                    }
                }
            }
        }
    }

    public void copyBadbankLabels(Wallet wallet, Set<BlockTransactionHashIndex> newTransactionOutputs) {
        if(wallet.getStandardAccountType() == StandardAccount.WHIRLPOOL_BADBANK && wallet.getMasterWallet() != null) {
            Map<BlockTransactionHashIndex, WalletNode> masterWalletTxos = wallet.getMasterWallet().getWalletTxos();
            for(BlockTransactionHashIndex newRef : newTransactionOutputs) {
                BlockTransactionHashIndex prevRef = masterWalletTxos.keySet().stream()
                        .filter(txo -> txo.isSpent() && txo.getSpentBy().getHash().equals(newRef.getHash()) && txo.getLabel() != null).findFirst().orElse(null);
                if(prevRef != null) {
                    if(newRef.getLabel() == null && prevRef.getLabel() != null) {
                        newRef.setLabel("From " + prevRef.getLabel());
                    }
                }
            }
        }
    }

    public Map<Sha256Hash, BlockTransaction> getReferencedTransactions(Set<Sha256Hash> references, String scriptHash) throws ServerException {
        Set<String> txids = new LinkedHashSet<>(references.size());
        for(Sha256Hash reference : references) {
            txids.add(reference.toString());
        }

        Map<String, VerboseTransaction> result = electrumServerRpc.getVerboseTransactions(getTransport(), txids, scriptHash);

        try {
            Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
            for(String txid : result.keySet()) {
                Sha256Hash hash = Sha256Hash.wrap(txid);
                VerboseTransaction verboseTransaction = result.get(txid);
                if(!hash.equals(Sha256Hash.wrap(verboseTransaction.txid))) {
                    log.error("Server returned transaction " + verboseTransaction.txid + " for requested txid " + hash);
                    throw new ServerException("Server returned a transaction that does not match the requested txid " + hash);
                }

                transactionMap.put(hash, verboseTransaction.getBlockTransaction());
            }

            return transactionMap;
        } catch(RuntimeException e) {
            log.error("Could not retrieve referenced transactions", e);
            throw new ServerException("Could not retrieve referenced transactions", e);
        }
    }

    public Map<Integer, Double> getFeeEstimates(List<Integer> targetBlocks, boolean useCached) throws ServerException {
        Map<Integer, Double> targetBlocksFeeRatesSats = getDefaultFeeEstimates(targetBlocks);

        FeeRatesSource feeRatesSource = Config.get().getFeeRatesSource();
        feeRatesSource = (feeRatesSource == null ? FeeRatesSource.MEMPOOL_SPACE : feeRatesSource);
        if(!feeRatesSource.isExternal()) {
            targetBlocksFeeRatesSats.putAll(feeRatesSource.getBlockTargetFeeRates(targetBlocksFeeRatesSats));
        } else if(useCached) {
            if(AppServices.getTargetBlockFeeRates() != null) {
                targetBlocksFeeRatesSats.putAll(AppServices.getTargetBlockFeeRates());
            }
        } else if(feeRatesSource.supportsNetwork(Network.get())) {
            targetBlocksFeeRatesSats.putAll(feeRatesSource.getBlockTargetFeeRates(targetBlocksFeeRatesSats));
        }

        return targetBlocksFeeRatesSats;
    }

    public Double getNextBlockMedianFeeRate() {
        FeeRatesSource feeRatesSource = Config.get().getFeeRatesSource();
        feeRatesSource = (feeRatesSource == null ? FeeRatesSource.MEMPOOL_SPACE : feeRatesSource);
        if(feeRatesSource.supportsNetwork(Network.get())) {
            try {
                return feeRatesSource.getNextBlockMedianFeeRate();
            } catch(Exception e) {
                return null;
            }
        }

        return null;
    }

    public Map<Integer, Double> getDefaultFeeEstimates(List<Integer> targetBlocks) throws ServerException {
        try {
            Map<Integer, Double> targetBlocksFeeRatesBtcKb = electrumServerRpc.getFeeEstimates(getTransport(), targetBlocks);

            Map<Integer, Double> targetBlocksFeeRatesSats = new TreeMap<>();
            for(Integer target : targetBlocksFeeRatesBtcKb.keySet()) {
                long minFeeRateSatsKb = (long)(targetBlocksFeeRatesBtcKb.get(target) * Transaction.SATOSHIS_PER_BITCOIN);
                if(minFeeRateSatsKb < 0) {
                    minFeeRateSatsKb = 1000;
                }
                targetBlocksFeeRatesSats.put(target, minFeeRateSatsKb / 1000d);
            }

            return targetBlocksFeeRatesSats;
        } catch(ElectrumServerRpcException e) {
            log.warn(e.getMessage());
            return targetBlocks.stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> AppServices.getFallbackFeeRate(),
                    (u, v) -> { throw new IllegalStateException("Duplicate target blocks"); },
                    LinkedHashMap::new));
        }
    }

    public Set<MempoolRateSize> getMempoolRateSizes() throws ServerException {
        Map<Double, Long> feeRateHistogram = electrumServerRpc.getFeeRateHistogram(getTransport());
        Set<MempoolRateSize> mempoolRateSizes = new TreeSet<>();
        for(Double fee : feeRateHistogram.keySet()) {
            mempoolRateSizes.add(new MempoolRateSize(fee, feeRateHistogram.get(fee)));
        }

        return mempoolRateSizes;
    }

    public Double getMinimumRelayFee() throws ServerException {
        Double minFeeRateBtcKb = electrumServerRpc.getMinimumRelayFee(getTransport());
        if(minFeeRateBtcKb != null) {
            long minFeeRateSatsKb = (long)(minFeeRateBtcKb * Transaction.SATOSHIS_PER_BITCOIN);
            double minFeeRate = minFeeRateSatsKb / 1000d;
            if(minFeeRate >= 0d && minFeeRate <= AppServices.getLongFeeRatesRange().getLast()) {
                return minFeeRate;
            }

            log.warn("Server returned an out of range minimum relay fee of " + minFeeRateBtcKb + " BTC/kB, using default");
        }

        return Transaction.DEFAULT_MIN_RELAY_FEE;
    }

    public Map<Integer, BlockSummary> getRecentBlockSummaryMap() throws ServerException {
        return getBlockSummaryMap(null, null);
    }

    public Map<Integer, BlockSummary> getBlockSummaryMap(Integer height, BlockHeader blockHeader) throws ServerException {
        if(serverCapability.supportsBlockStats()) {
            if(height == null) {
                Integer current = AppServices.getCurrentBlockHeight();
                if(current == null) {
                    return Collections.emptyMap();
                }
                Set<Integer> heights = IntStream.range(current - 1, current + 1).boxed().collect(Collectors.toSet());
                Map<Integer, BlockStats> blockStats = electrumServerRpc.getBlockStats(getTransport(), heights);
                return blockStats.keySet().stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> blockStats.get(v).toBlockSummary()));
            } else {
                Map<Integer, BlockStats> blockStats = electrumServerRpc.getBlockStats(getTransport(), Set.of(height));
                return blockStats.keySet().stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> blockStats.get(v).toBlockSummary()));
            }
        }

        FeeRatesSource feeRatesSource = Config.get().getFeeRatesSource();
        feeRatesSource = (feeRatesSource == null ? FeeRatesSource.MEMPOOL_SPACE : feeRatesSource);

        if(feeRatesSource.supportsNetwork(Network.get())) {
            try {
                if(blockHeader == null) {
                    return feeRatesSource.getRecentBlockSummaries();
                } else {
                    Map<Integer, BlockSummary> blockSummaryMap = new HashMap<>();
                    BlockSummary blockSummary = feeRatesSource.getBlockSummary(Sha256Hash.twiceOf(blockHeader.bitcoinSerialize()));
                    if(blockSummary != null && blockSummary.getHeight() != null) {
                        blockSummaryMap.put(blockSummary.getHeight(), blockSummary);
                    }
                    return blockSummaryMap;
                }
            } catch(Exception e) {
                return getServerBlockSummaryMap(height, blockHeader);
            }
        } else {
            return getServerBlockSummaryMap(height, blockHeader);
        }
    }

    private Map<Integer, BlockSummary> getServerBlockSummaryMap(Integer height, BlockHeader blockHeader) throws ServerException {
        if(blockHeader == null || height == null) {
            Integer current = AppServices.getCurrentBlockHeight();
            if(current == null) {
                return Collections.emptyMap();
            }
            Set<BlockTransactionHash> references = IntStream.range(current - 1, current + 1)
                    .mapToObj(i -> new BlockTransaction(null, i, null, null, null)).collect(Collectors.toSet());
            Map<Integer, BlockHeader> blockHeaders = getBlockHeaders(null, references);
            return blockHeaders.keySet().stream()
                    .collect(Collectors.toMap(java.util.function.Function.identity(), v -> new BlockSummary(v, blockHeaders.get(v).getTimeAsDate())));
        } else {
            Map<Integer, BlockSummary> blockSummaryMap = new HashMap<>();
            blockSummaryMap.put(height, new BlockSummary(height, blockHeader.getTimeAsDate()));
            return blockSummaryMap;
        }
    }

    public List<BlockTransaction> getRecentMempoolTransactions() {
        FeeRatesSource feeRatesSource = Config.get().getFeeRatesSource();
        feeRatesSource = (feeRatesSource == null ? FeeRatesSource.MEMPOOL_SPACE : feeRatesSource);

        if(feeRatesSource.supportsNetwork(Network.get())) {
            try {
                List<BlockTransactionHash> recentTransactions = feeRatesSource.getRecentMempoolTransactions();
                Map<BlockTransactionHash, Transaction> setReferences = new HashMap<>();
                setReferences.put(recentTransactions.getFirst(), null);
                if(recentTransactions.size() > 1) {
                    Random random = new Random();
                    int halfSize = recentTransactions.size() / 2;
                    setReferences.put(recentTransactions.get(halfSize == 1 ? 1 : random.nextInt(halfSize) + 1), null);
                }
                Map<Sha256Hash, BlockTransaction> transactions = getTransactions(null, setReferences, Collections.emptyMap());
                return transactions.values().stream().filter(blxTx -> blxTx.getTransaction() != null).toList();
            } catch(Exception e) {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    public Sha256Hash broadcastTransaction(Transaction transaction, Long fee) throws ServerException {
        //Eagerly populate broadcastedTransactions before broadcasting and roll back on broadcast failure
        Sha256Hash txid = transaction.getTxId();
        BlockTransaction blkTx = new BlockTransaction(txid, 0, null, fee, transaction);
        broadcastedTransactions.put(txid, blkTx);

        try {
            Sha256Hash broadcastTxid = broadcastTransactionPrivately(transaction);
            if(broadcastTxid == null) {
                broadcastedTransactions.remove(txid);
            }
            return broadcastTxid;
        } catch(Exception e) {
            broadcastedTransactions.remove(txid);
            throw e;
        }
    }

    public Sha256Hash broadcastTransactionPrivately(Transaction transaction) throws ServerException {
        //If Tor proxy is configured, try all external broadcast sources in random order before falling back to connected Electrum server
        if(AppServices.isUsingProxy()) {
            List<BroadcastSource> broadcastSources = Arrays.stream(BroadcastSource.values()).filter(src -> src.getSupportedNetworks().contains(Network.get())).collect(Collectors.toList());
            Sha256Hash txid = null;
            for(int i = 1; !broadcastSources.isEmpty(); i++) {
                BroadcastSource broadcastSource = broadcastSources.remove(new Random().nextInt(broadcastSources.size()));
                try {
                    txid = broadcastSource.broadcastTransaction(transaction);
                    if(Network.get() != Network.MAINNET || i >= MINIMUM_BROADCASTS || broadcastSources.isEmpty()) {
                        return txid;
                    }
                } catch(BroadcastSource.BroadcastException e) {
                    log.error("Could not post transaction via " + broadcastSource.getName(), e);
                }
            }

            if(txid != null) {
                return txid;
            }
        }

        return broadcastTransaction(transaction);
    }

    public Sha256Hash broadcastTransaction(Transaction transaction) throws ServerException {
        byte[] rawtxBytes = transaction.bitcoinSerialize();
        String rawtxHex = Utils.bytesToHex(rawtxBytes);

        try {
            String strTxHash = electrumServerRpc.broadcastTransaction(getTransport(), rawtxHex);
            Sha256Hash receivedTxid = Sha256Hash.wrap(strTxHash);
            if(!receivedTxid.equals(transaction.getTxId())) {
                throw new ServerException("Received txid was different (" + receivedTxid + ")");
            }

            return receivedTxid;
        } catch(ElectrumServerRpcException | IllegalStateException e) {
            throw new ServerException(e.getMessage(), e);
        }
    }

    public Set<String> getMempoolScriptHashes(Wallet wallet, Sha256Hash txId, Set<WalletNode> transactionNodes) throws ServerException {
        Map<String, String> pathScriptHashes = new LinkedHashMap<>(transactionNodes.size());
        for(WalletNode node : transactionNodes) {
            pathScriptHashes.put(node.getDerivationPath(), getScriptHash(node));
        }

        Set<String> mempoolScriptHashes = new LinkedHashSet<>();
        Map<String, ScriptHashTx[]> result = electrumServerRpc.getScriptHashHistory(getTransport(), wallet, pathScriptHashes, true);
        for(String path : result.keySet()) {
            ScriptHashTx[] txes = result.get(path);
            if(Arrays.stream(txes).map(ScriptHashTx::getBlockchainTransactionHash).anyMatch(ref -> txId.equals(ref.getHash()) && ref.getHeight() <= 0)) {
                mempoolScriptHashes.add(pathScriptHashes.get(path));
            }
        }

        return mempoolScriptHashes;
    }

    public List<TransactionOutput> getUtxos(Address address) throws ServerException {
        Wallet wallet = new Wallet(address.toString());
        Map<String, String> pathScriptHashes = new HashMap<>();
        pathScriptHashes.put("m/0", getScriptHash(address));
        Map<String, ScriptHashTx[]> historyResult = electrumServerRpc.getScriptHashHistory(getTransport(), wallet, pathScriptHashes, true);
        Set<String> txids = Arrays.stream(historyResult.get("m/0")).map(scriptHashTx -> scriptHashTx.tx_hash).collect(Collectors.toSet());

        Map<String, String> transactionsResult = electrumServerRpc.getTransactions(getTransport(), wallet, txids);
        List<TransactionOutput> transactionOutputs = new ArrayList<>();
        Script outputScript = address.getOutputScript();
        String strErrorTx = Sha256Hash.ZERO_HASH.toString();
        List<Transaction> transactions = new ArrayList<>();
        for(String txid : transactionsResult.keySet()) {
            String strRawTx = transactionsResult.get(txid);

            if(strRawTx.equals(strErrorTx)) {
                continue;
            }

            Transaction transaction;

            try {
                transaction = new Transaction(Utils.hexToBytes(strRawTx));
            } catch(Exception e) {
                log.error("Could not parse tx: " + strRawTx, e);
                continue;
            }

            if(!transaction.getTxId().toString().equalsIgnoreCase(txid)) {
                log.error("Server returned transaction " + transaction.getTxId() + " for requested txid " + txid);
                throw new ServerException("Server returned a transaction that does not match the requested txid " + txid);
            }

            for(TransactionOutput txOutput : transaction.getOutputs()) {
                if(txOutput.getScript().equals(outputScript)) {
                    transactionOutputs.add(txOutput);
                }
            }
            transactions.add(transaction);
        }

        for(Transaction transaction : transactions) {
            for(TransactionInput txInput : transaction.getInputs()) {
                transactionOutputs.removeIf(txOutput -> txOutput.getHash().equals(txInput.getOutpoint().getHash()) && txOutput.getIndex() == txInput.getOutpoint().getIndex());
            }
        }

        return transactionOutputs;
    }

    public static Map<String, WalletNode> getAllScriptHashes(Wallet wallet) {
        Map<String, WalletNode> scriptHashes = new HashMap<>();
        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
            for(WalletNode childNode : wallet.getNode(keyPurpose).getChildren()) {
                scriptHashes.put(getScriptHash(childNode), childNode);
            }
        }

        return scriptHashes;
    }

    private static TransactionOutput getPrevOutput(Wallet wallet, TransactionInput txInput) {
        try {
            return wallet.getWalletTransaction(txInput.getOutpoint().getHash()).getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
        } catch(Exception e) {
            return null;
        }
    }

    public static String getScriptHash(WalletNode node) {
        byte[] hash = Sha256Hash.hash(node.getOutputScript().getProgram());
        byte[] reversed = Utils.reverseBytes(hash);
        return Utils.bytesToHex(reversed);
    }

    public static String getScriptHash(TransactionOutput output) {
        byte[] hash = Sha256Hash.hash(output.getScript().getProgram());
        byte[] reversed = Utils.reverseBytes(hash);
        return Utils.bytesToHex(reversed);
    }

    public static String getScriptHash(Address address) {
        byte[] hash = Sha256Hash.hash(address.getOutputScript().getProgram());
        byte[] reversed = Utils.reverseBytes(hash);
        return Utils.bytesToHex(reversed);
    }

    public static Map<String, List<String>> getSubscribedScriptHashes() {
        return subscribedScriptHashes;
    }

    public static void requireSilentPaymentsSupport() {
        if(serverCapability == null || !serverCapability.supportsSilentPayments()) {
            throw new ElectrumServerRpcException("This server does not support silent payments");
        }
    }

    static SilentPaymentsScanCache getScanCache(String spAddress) {
        return spScanCaches.get(spAddress);
    }

    public static boolean hasSilentPaymentsCache(SilentPaymentScanAddress scanAddress) {
        return spScanCaches.containsKey(scanAddress.getAddress());
    }

    public static boolean isSilentPaymentsFullyCovered(SilentPaymentScanAddress scanAddress) {
        if(scanAddress == null) {
            return false;
        }

        SilentPaymentsScanCache cache = spScanCaches.get(scanAddress.getAddress());
        if(cache == null) {
            return false;
        }

        cache.lock();
        try {
            Integer serverStart = cache.getServerStart();
            if(!cache.isCompleted() || serverStart == null) {
                return false;
            }
            int earliestPossibleStart = Network.get() == Network.MAINNET ? TAPROOT_ACTIVATION_HEIGHT : 0;
            return serverStart <= earliestPossibleStart;
        } finally {
            cache.unlock();
        }
    }

    public static Cormorant getCormorant() {
        return cormorant;
    }

    private static void cancelSilentPaymentScans() {
        for(SilentPaymentsScanCache cache : spScanCaches.values()) {
            cache.lock();
            try {
                cache.cancel();
            } finally {
                cache.unlock();
            }
        }
    }

    /**
     * Holds a silent-payment subscription for the given scan address. Increments the per-cache refcount,
     * issuing a subscribe RPC the first time the cache is established and re-issuing if the needed start
     * is lower than the current subscription's start. On RPC failure the refcount is rolled back.
     * Callers must pair every successful hold with a matching call.
     */
    public static void holdSilentPaymentSubscription(Wallet wallet, SilentPaymentScanAddress scanAddress, int neededStart) throws ServerException {
        requireSilentPaymentsSupport();
        String spAddress = scanAddress.getAddress();
        SilentPaymentsScanCache cache = spScanCaches.computeIfAbsent(spAddress, k -> new SilentPaymentsScanCache());

        boolean needSubscribe;
        SilentPaymentsScanCache.Snapshot rollbackSnapshot = null;
        cache.lock();
        try {
            boolean isFirstCaller = cache.incrementRefCount() == 1;
            //If a concurrent caller is currently establishing the subscription, wait for serverStart to be
            //captured before making our widening decision. awaitSubscriptionComplete() releases the cache
            //lock during the wait, allowing notification handlers to proceed (avoids deadlock with
            //TcpTransport's read thread).
            while(cache.hasMultipleHolders() && cache.getServerStart() == null && cache.isScanning()) {
                try {
                    cache.awaitSubscriptionComplete();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if(cache.decrementRefCount()) {
                        spScanCaches.remove(spAddress);
                    }
                    throw new ServerException("Interrupted waiting for silent payments subscription to establish", e);
                }
            }

            if(cache.isCancelled()) {
                //First caller's subscribe failed (or scan was cancelled) — propagate.
                if(cache.decrementRefCount()) {
                    spScanCaches.remove(spAddress);
                }
                throw new ServerException("Silent payments subscription failed for " + spAddress);
            }

            if(isFirstCaller) {
                //Cache was just created by computeIfAbsent above, with all defaults. Establish the subscription.
                needSubscribe = true;
            } else if(needsWiderCoverage(neededStart, cache.getServerStart())) {
                //New caller wants earlier history than current coverage; widen and trigger a rescan.
                rollbackSnapshot = cache.captureSnapshot();
                cache.restartScan();
                needSubscribe = true;
            } else {
                needSubscribe = false;
            }
        } finally {
            cache.unlock();
        }

        if(needSubscribe) {
            try {
                String scanPrivHex = Utils.bytesToHex(scanAddress.getScanKey().getPrivKeyBytes());
                String spendPubHex = Utils.bytesToHex(scanAddress.getSpendKey().getPubKey(true));
                SilentPaymentsSubscription response = electrumServerRpc.subscribeSilentPayments(getTransport(), wallet, scanPrivHex, spendPubHex, neededStart, NO_LABELS);
                cache.lock();
                try {
                    cache.setServerStart(response.start_height);
                } finally {
                    cache.unlock();
                }
            } catch(Exception e) {
                cache.lock();
                try {
                    if(rollbackSnapshot != null && cache.hasMultipleHolders()) {
                        cache.restoreFromSnapshot(rollbackSnapshot);
                    } else {
                        cache.cancel();
                    }
                    if(cache.decrementRefCount()) {
                        spScanCaches.remove(spAddress);
                    }
                } finally {
                    cache.unlock();
                }
                throw e;
            }
        }
    }

    private static boolean needsWiderCoverage(int neededStart, int serverStart) {
        boolean neededIsTimestamp = neededStart >= Transaction.MAX_BLOCK_LOCKTIME;
        boolean serverIsTimestamp = serverStart >= Transaction.MAX_BLOCK_LOCKTIME;
        if(neededIsTimestamp != serverIsTimestamp) {
            return neededIsTimestamp;
        }
        return neededStart < serverStart;
    }

    public List<SilentPaymentsTx> getSilentPaymentHistory(SilentPaymentScanAddress scanAddress) throws ServerException {
        String spAddress = scanAddress.getAddress();
        SilentPaymentsScanCache cache = spScanCaches.get(spAddress);
        if(cache == null) {
            throw new IllegalStateException("No silent payments subscription is held for " + spAddress);
        }

        cache.lock();
        try {
            while(cache.isScanning()) {
                try {
                    cache.awaitScanComplete();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServerException("Interrupted waiting for silent payments scan to complete", e);
                }
            }
            if(cache.isCancelled()) {
                throw new ServerException("Silent payments scan was cancelled for " + spAddress.substring(0, 10) + "...");
            }
            return cache.snapshotEntries();
        } finally {
            cache.unlock();
        }
    }

    public static void releaseSilentPaymentSubscription(SilentPaymentScanAddress scanAddress) {
        String spAddress = scanAddress.getAddress();
        SilentPaymentsScanCache cache = spScanCaches.get(spAddress);
        if(cache == null) {
            return;
        }

        boolean unsubscribe;
        cache.lock();
        try {
            unsubscribe = cache.decrementRefCount();
            if(unsubscribe) {
                cache.cancel();
                spScanCaches.remove(spAddress);
            }
        } finally {
            cache.unlock();
        }

        if(unsubscribe) {
            Platform.runLater(() -> EventManager.get().post(new SilentPaymentsUnsubscribeEvent(scanAddress)));
        }
    }

    public Set<WalletNode> processSilentPaymentBatch(Wallet wallet, List<SilentPaymentsTx> entries) throws ServerException {
        if(entries.isEmpty()) {
            return Collections.emptySet();
        }

        Map<BlockTransactionHash, Transaction> referencesToFetch = new TreeMap<>();
        Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
        Map<Sha256Hash, byte[]> tweakMap = new HashMap<>();
        //Track which batch txids the wallet already had. Everything else in transactionMap (via
        //broadcastedTransactions or via the fetch below) is newly introduced to the wallet by this
        //batch and needs its spent-input nodes identified.
        Set<Sha256Hash> alreadyInWallet = new HashSet<>();
        for(SilentPaymentsTx entry : entries) {
            Sha256Hash txid;
            byte[] tweakKey;
            try {
                txid = Sha256Hash.wrap(entry.tx_hash);
                tweakKey = Utils.hexToBytes(entry.tweak_key);
                if(tweakKey.length != 33) {
                    throw new ProtocolException("Tweak key must be 33 bytes, not " + tweakKey.length);
                }
            } catch(NullPointerException | ProtocolException e) {
                log.warn("Skipping malformed silent payments entry " + entry + ": " + e);
                continue;
            }

            tweakMap.putIfAbsent(txid, tweakKey);
            BlockTransaction existing = wallet.getWalletTransaction(txid);
            if(existing != null) {
                transactionMap.put(txid, existing);
                alreadyInWallet.add(txid);
            } else {
                existing = broadcastedTransactions.get(txid);
                if(existing != null) {
                    transactionMap.put(txid, existing);
                } else {
                    referencesToFetch.put(new BlockTransaction(txid, entry.height, null, null, null), null);
                }
            }
        }

        if(!referencesToFetch.isEmpty()) {
            try {
                //The second write boundary: these heights come from the subscription rather than from a history call, and reach the wallet just the same
                Map<BlockTransactionHash, BlockHeader> proven = isVerifyingTransactions() ? verifySilentPaymentReferences(wallet, referencesToFetch) : Collections.emptyMap();
                Map<Integer, BlockHeader> blockHeaderMap = getBlockHeaders(wallet, referencesToFetch.keySet());
                Map<Sha256Hash, BlockTransaction> fetched = getTransactions(wallet, referencesToFetch, blockHeaderMap, proven);
                transactionMap.putAll(fetched);
                wallet.updateTransactions(fetched);
            } finally {
                postProofEvents(wallet);
            }
        }

        ECKey scanPriv = wallet.getSilentPaymentScanAddress().getScanKey();
        ECKey spendPub = wallet.getSilentPaymentScanAddress().getSpendKey();

        Map<Address, WalletNode> walletAddresses = wallet.getWalletAddresses();
        Set<WalletNode> affectedNodes = new LinkedHashSet<>();

        int receiveNextIndex = nextIndex(wallet.getNode(KeyPurpose.RECEIVE));
        int changeNextIndex = nextIndex(wallet.getNode(KeyPurpose.CHANGE));

        for(Map.Entry<Sha256Hash, BlockTransaction> entry : transactionMap.entrySet()) {
            Sha256Hash txid = entry.getKey();
            Transaction tx = entry.getValue().getTransaction();
            byte[] tweakKey = tweakMap.get(txid);
            if(tweakKey == null) {
                continue;
            }
            try {
                List<SilentPaymentScanMatch> matches = SilentPaymentUtils.scanTransactionOutputs(scanPriv, spendPub, Collections.emptySet(), tweakKey, tx.getOutputs());
                for(SilentPaymentScanMatch match : matches) {
                    KeyPurpose purpose = match.labelIndex() != null && match.labelIndex() == 0 ? KeyPurpose.CHANGE : KeyPurpose.RECEIVE;
                    int newIndex = purpose == KeyPurpose.CHANGE ? changeNextIndex : receiveNextIndex;
                    WalletNode newNode = createNodeForMatch(wallet, match, walletAddresses, purpose, newIndex);
                    if(newNode != null) {
                        affectedNodes.add(newNode);
                        walletAddresses.put(wallet.getAddress(newNode), newNode);

                        //Pre-populate the new SP node's transactionOutputs from the match data so the
                        //first WalletHistoryChangedEvent's isComplete check sees both sides of the spend
                        //already linked. Without this, the new SP node remains unmarked in
                        //subscribeWalletNodes (its subscribe-response status is typically null if
                        //server's scripthash index lags the SP discovery channel), calculateNodeHistory
                        //never runs for it in this pass, and the user sees a partial first notification
                        //(spend without self-change credit) corrected by a second notification when the
                        //scripthash push catches up. A later refresh's calculateNodeHistory rebuilds the
                        //TXOs from authoritative server history; the matching-(hash,index) preservation
                        //logic in WalletNode.updateTransactionOutputs keeps labels/statuses intact.
                        TransactionOutput output = tx.getOutputs().get(match.outputIndex());
                        BlockTransaction blkTx = entry.getValue();
                        Set<BlockTransactionHashIndex> initialTxos = new TreeSet<>();
                        initialTxos.add(new BlockTransactionHashIndex(txid, blkTx.getHeight(), blkTx.getDate(), blkTx.getFee(), match.outputIndex(), output.getValue()));
                        newNode.updateTransactionOutputs(wallet, initialTxos);

                        if(purpose == KeyPurpose.CHANGE) {
                            changeNextIndex++;
                        } else {
                            receiveNextIndex++;
                        }
                    }
                }
            } catch(InvalidSilentPaymentException | IllegalArgumentException e) {
                log.warn("Invalid silent payment tweak for tx " + txid + " — skipping", e);
            }
        }

        //Include wallet nodes whose UTXOs are spent by any tx newly introduced to the wallet by this
        //batch (either fetched here or pulled in from broadcastedTransactions), so calculateNodeHistory
        //runs for them in the same atomic pass and sets spentBy on the spent TXOs.
        Map<HashIndex, WalletNode> walletTxoNodes = new HashMap<>();
        Map<HashIndex, BlockTransactionHashIndex> walletTxoIndex = new HashMap<>();
        for(Map.Entry<BlockTransactionHashIndex, WalletNode> entry : wallet.getWalletTxos().entrySet()) {
            HashIndex hashIndex = new HashIndex(entry.getKey().getHash(), entry.getKey().getIndex());
            walletTxoNodes.put(hashIndex, entry.getValue());
            walletTxoIndex.put(hashIndex, entry.getKey());
        }
        Set<WalletNode> spentInputNodes = new LinkedHashSet<>();
        Map<WalletNode, Map<HashIndex, BlockTransactionHashIndex>> nodeSpendsByHashIndex = new LinkedHashMap<>();
        for(Map.Entry<Sha256Hash, BlockTransaction> entry : transactionMap.entrySet()) {
            if(alreadyInWallet.contains(entry.getKey())) {
                continue;
            }
            Sha256Hash spendingTxid = entry.getKey();
            BlockTransaction spendingBlkTx = entry.getValue();
            List<TransactionInput> inputs = spendingBlkTx.getTransaction().getInputs();
            for(int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                TransactionInput input = inputs.get(inputIndex);
                HashIndex inputHashIndex = new HashIndex(input.getOutpoint().getHash(), input.getOutpoint().getIndex());
                WalletNode node = walletTxoNodes.get(inputHashIndex);
                if(node == null) {
                    continue;
                }
                spentInputNodes.add(node);

                BlockTransactionHashIndex existingTxo = walletTxoIndex.get(inputHashIndex);
                if(existingTxo == null || existingTxo.getSpentBy() != null) {
                    //Already-spent UTXO (e.g. RBF chain) — leave the prior spentBy untouched and let
                    //calculateNodeHistory reconcile on the next refresh against server-authoritative data.
                    continue;
                }
                BlockTransactionHashIndex spendingTxi = new BlockTransactionHashIndex(spendingTxid, spendingBlkTx.getHeight(), spendingBlkTx.getDate(), spendingBlkTx.getFee(),
                        inputIndex, existingTxo.getValue());
                nodeSpendsByHashIndex.computeIfAbsent(node, n -> new HashMap<>()).put(inputHashIndex, spendingTxi);
            }
        }
        affectedNodes.addAll(spentInputNodes);

        //Apply the spentBy pre-populates via TreeSet rebuild for each affected node.
        for(Map.Entry<WalletNode, Map<HashIndex, BlockTransactionHashIndex>> entry : nodeSpendsByHashIndex.entrySet()) {
            WalletNode node = entry.getKey();
            Map<HashIndex, BlockTransactionHashIndex> spendsByHashIndex = entry.getValue();
            Set<BlockTransactionHashIndex> rebuilt = new TreeSet<>();
            for(BlockTransactionHashIndex txo : new ArrayList<>(node.getTransactionOutputs())) {
                BlockTransactionHashIndex spendingTxi = spendsByHashIndex.get(new HashIndex(txo.getHash(), txo.getIndex()));
                if(spendingTxi != null && txo.getSpentBy() == null) {
                    rebuilt.add(new BlockTransactionHashIndex(txo.getHash(), txo.getHeight(), txo.getDate(), txo.getFee(), txo.getIndex(), txo.getValue(), spendingTxi));
                } else {
                    rebuilt.add(txo);
                }
            }
            node.updateTransactionOutputs(wallet, rebuilt);
        }

        return affectedNodes;
    }

    private static int nextIndex(WalletNode purposeNode) {
        return purposeNode.getChildren().isEmpty() ? 0 : purposeNode.getChildren().stream().mapToInt(WalletNode::getIndex).max().getAsInt() + 1;
    }

    private WalletNode createNodeForMatch(Wallet wallet, SilentPaymentScanMatch match, Map<Address, WalletNode> walletAddresses, KeyPurpose purpose, int newIndex) {
        WalletNode purposeNode = wallet.getNode(purpose);
        WalletNode addressNode = purposeNode.addSilentPaymentChild(wallet, newIndex, match.tweak());
        if(addressNode == null) {
            throw new IllegalStateException("Silent payment child already exists at index " + newIndex);
        }

        if(walletAddresses.containsKey(wallet.getAddress(addressNode))) {
            purposeNode.getChildren().remove(addressNode);
            return null;
        }

        return addressNode;
    }

    public static String getSubscribedScriptHashStatus(String scriptHash) {
        List<String> existingStatuses = subscribedScriptHashes.get(scriptHash);
        if(existingStatuses != null && !existingStatuses.isEmpty()) {
            return existingStatuses.get(existingStatuses.size() - 1);
        }

        return null;
    }

    public static void updateSubscribedScriptHashStatus(String scriptHash, String status) {
        List<String> existingStatuses = subscribedScriptHashes.computeIfAbsent(scriptHash, k -> new ArrayList<>());
        existingStatuses.add(status);
    }

    public static void updateRetrievedBlockHeaders(Integer blockHeight, BlockHeader blockHeader) {
        retrievedBlockHeaders.put(blockHeight, blockHeader);
    }

    /**
     * Sanity checks a server announced chain tip, returning the reason it is invalid, or null if it is valid.
     * The header must parse, must not be timestamped in the future, and must meet its own claimed proof of work target.
     */
    static String getTipValidationError(BlockHeaderTip tip) {
        return getTipValidationError(tip, System.currentTimeMillis());
    }

    static String getTipValidationError(BlockHeaderTip tip, long now) {
        try {
            if(tip.height < 0 || tip.hex == null) {
                return "Announced block header tip at height " + tip.height + " is missing or malformed";
            }

            BlockHeader blockHeader = tip.getBlockHeader();
            if(!blockHeader.verifyProofOfWork()) {
                return "Announced block header at height " + tip.height + " does not meet its claimed proof of work target";
            }

            long nowSecs = now / 1000;
            if(blockHeader.getTime() > nowSecs + MAXIMUM_FUTURE_TIP_TIME_SECS) {
                return "Announced block header at height " + tip.height + " is timestamped " + ((blockHeader.getTime() - nowSecs) / 3600) + " hours in the future, indicating either an invalid header or a slow system clock";
            }

            return null;
        } catch(Exception e) {
            return "Error parsing announced block header at height " + tip.height + ": " + e;
        }
    }

    /**
     * Logs and shows a status warning for an invalid tip. A hostile server can send invalid tips at any rate, so warnings are rate limited,
     * and the status warning is shown at most once per episode of invalid tips (reset on any valid tip).
     */
    static void warnInvalidTip(String message) {
        long now = System.currentTimeMillis();
        if(now - lastTipWarningLoggedAt > TIP_WARNING_INTERVAL_MILLIS) {
            lastTipWarningLoggedAt = now;
            log.warn(message);

            if(!invalidTipWarned) {
                invalidTipWarned = true;
                Platform.runLater(() -> EventManager.get().post(new StatusEvent("Warning: Ignoring invalid block header announced by the server", 120)));
            }
        }
    }

    private static void initializeTip(BlockHeaderTip tip) {
        updateTipReceived();
        //Public servers are never mid-sync, so seed the staleness clock from the tip timestamp to warn promptly on an already stale server
        if(Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER) {
            lastTipReceivedAt = Math.min(lastTipReceivedAt, tip.getBlockHeader().getTime() * 1000);
        }
    }

    static void updateTipReceived() {
        lastTipReceivedAt = System.currentTimeMillis();
        staleTipWarned = false;
        invalidTipWarned = false;
    }

    public static ServerCapability getServerCapability(List<String> serverVersion) {
        if(!serverVersion.isEmpty()) {
            String server = serverVersion.getFirst().toLowerCase(Locale.ROOT);
            if(server.contains("electrumx")) {
                return new ServerCapability(true, true, true);
            }

            if(server.startsWith("frigate")) {
                return new ServerCapability(true, true, true);
            }

            if(server.startsWith("cormorant")) {
                return new ServerCapability(true, false, true, false, true).withMerkleProofs(false);
            }

            if(server.startsWith("electrs/")) {
                String electrsVersion = server.substring("electrs/".length());
                int dashIndex = electrsVersion.indexOf('-');
                if(dashIndex > -1) {
                    electrsVersion = electrsVersion.substring(0, dashIndex);
                }
                try {
                    Version version = new Version(electrsVersion);
                    if(version.compareTo(ELECTRS_MIN_BATCHING_VERSION) >= 0) {
                        return new ServerCapability(true, true, true);
                    }
                } catch(Exception e) {
                    //ignore
                }
            }

            if(server.startsWith("fulcrum")) {
                String fulcrumVersion = server.substring("fulcrum".length()).trim();
                int dashIndex = fulcrumVersion.indexOf('-');
                if(dashIndex > -1) {
                    fulcrumVersion = fulcrumVersion.substring(0, dashIndex);
                }
                try {
                    Version version = new Version(fulcrumVersion);
                    if(version.compareTo(FULCRUM_MIN_BATCHING_VERSION) >= 0) {
                        return new ServerCapability(true, true, true);
                    }
                } catch(Exception e) {
                    //ignore
                }
            }

            if(server.startsWith("mempool-electrs")) {
                String mempoolElectrsVersion = server.substring("mempool-electrs".length()).trim();
                int dashIndex = mempoolElectrsVersion.indexOf('-');
                String mempoolElectrsSuffix = "";
                if(dashIndex > -1) {
                    mempoolElectrsSuffix = mempoolElectrsVersion.substring(dashIndex);
                    mempoolElectrsVersion = mempoolElectrsVersion.substring(0, dashIndex);
                }
                try {
                    Version version = new Version(mempoolElectrsVersion);
                    if(version.compareTo(MEMPOOL_ELECTRS_MIN_BATCHING_VERSION) > 0 ||
                            (version.compareTo(MEMPOOL_ELECTRS_MIN_BATCHING_VERSION) == 0 && (!mempoolElectrsSuffix.contains("dev") || mempoolElectrsSuffix.contains("dev-249848d")))) {
                        return new ServerCapability(true, 25, false, true);
                    }
                } catch(Exception e) {
                    //ignore
                }
            }

            if(server.startsWith("electrumpersonalserver")) {
                return new ServerCapability(false, false, false);
            }
        }

        return new ServerCapability(false, true, true);
    }

    public static class ServerVersionService extends Service<List<String>> {
        @Override
        protected Task<List<String>> createTask() {
            return new Task<List<String>>() {
                protected List<String> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.getServerVersion();
                }
            };
        }
    }

    public static class ServerBannerService extends Service<String> {
        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.getServerBanner();
                }
            };
        }
    }

    public static class ConnectionService extends ScheduledService<FeeRatesUpdatedEvent> implements Thread.UncaughtExceptionHandler {
        private static final int FEE_RATES_PERIOD = 30 * 1000;

        private final boolean subscribe;
        private boolean firstCall = true;
        private Thread reader;
        private long feeRatesRetrievedAt;
        private final Bwt bwt = new Bwt();
        private final ReentrantLock bwtStartLock = new ReentrantLock();
        private final Condition bwtStartCondition = bwtStartLock.newCondition();
        private Throwable bwtStartException;
        private boolean shutdown;

        public ConnectionService() {
            this(true);
        }

        public ConnectionService(boolean subscribe) {
            this.subscribe = subscribe;
        }

        @Override
        protected Task<FeeRatesUpdatedEvent> createTask() {
            return new Task<>() {
                protected FeeRatesUpdatedEvent call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();

                    if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
                        try {
                            if(Config.get().isUseLegacyCoreWallet()) {
                                throw new CormorantBitcoindException("Legacy wallet configured");
                            }
                            if(ElectrumServer.cormorant == null) {
                                ElectrumServer.cormorant = new Cormorant(subscribe);
                                ElectrumServer.coreElectrumServer = cormorant.start();
                            }
                        } catch(CormorantBitcoindUnsupportedException e) {
                            throw new ServerException(e.getMessage());
                        } catch(CormorantBitcoindException e) {
                            ElectrumServer.cormorant = null;
                            log.debug("Cannot start cormorant: " + e.getMessage() + ". Starting BWT...");

                            Bwt.initialize();

                            if(!bwt.isRunning()) {
                                Bwt.ConnectionService bwtConnectionService = bwt.getConnectionService(subscribe);
                                bwtStartException = null;
                                bwtConnectionService.setOnFailed(workerStateEvent -> {
                                    log.error("Failed to start BWT", workerStateEvent.getSource().getException());
                                    bwtStartException = workerStateEvent.getSource().getException();
                                    try {
                                        bwtStartLock.lock();
                                        bwtStartCondition.signal();
                                    } finally {
                                        bwtStartLock.unlock();
                                    }
                                });
                                Platform.runLater(bwtConnectionService::start);

                                try {
                                    bwtStartLock.lock();
                                    bwtStartCondition.await();

                                    if(!bwt.isReady()) {
                                        if(bwtStartException != null) {
                                            Matcher walletLoadingMatcher = RPC_WALLET_LOADING_PATTERN.matcher(bwtStartException.getMessage());
                                            if(bwtStartException.getMessage().contains("Wallet file not specified")) {
                                                throw new ServerException("Bitcoin Core requires Multi-Wallet to be enabled in the Server Settings");
                                            } else if(bwtStartException.getMessage().contains("Upgrade Bitcoin Core to v24 or later for Taproot wallet support")) {
                                                throw new ServerException(bwtStartException.getMessage());
                                            } else if(bwtStartException.getMessage().contains("Wallet file verification failed. Refusing to load database.")) {
                                                throw new ServerException("Bitcoin Core wallet file verification failed. Try restarting Bitcoin Core.");
                                            } else if(bwtStartException.getMessage().contains("This error could be caused by pruning or data corruption")) {
                                                throw new ServerException("Scanning failed. Bitcoin Core is pruned to a date after the wallet birthday.");
                                            } else if(walletLoadingMatcher.matches() && walletLoadingMatcher.group(1) != null) {
                                                throw new ServerException(walletLoadingMatcher.group(1));
                                            }
                                        }

                                        throw new ServerException("Check if Bitcoin Core is running, and the authentication details are correct.");
                                    }
                                } catch(InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    return null;
                                } finally {
                                    bwtStartLock.unlock();
                                }
                            }
                        } catch(IllegalStateException e) {
                            if(e.getCause() instanceof SSLHandshakeException) {
                                throw new TlsServerException(Config.get().getCoreServer().getHostAndPort(), e.getCause());
                            } else {
                                throw e;
                            }
                        }
                    }

                    if(firstCall) {
                        electrumServer.connect();

                        reader = new Thread(new ReadRunnable(), "ElectrumServerReadThread");
                        reader.setDaemon(true);
                        reader.setUncaughtExceptionHandler(ConnectionService.this);
                        reader.start();

                        //Start with simple RPC for maximum compatibility
                        electrumServerRpc = new SimpleElectrumServerRpc();

                        List<String> serverVersion = electrumServer.getServerVersion();
                        firstCall = false;

                        serverCapability = getServerCapability(serverVersion);
                        if(serverCapability.supportsBatching()) {
                            log.debug("Upgrading to batched JSON-RPC");
                            electrumServerRpc = new BatchedElectrumServerRpc(electrumServerRpc.getIdCounterValue(), serverCapability.getMaxTargetBlocks());
                        }

                        if(serverCapability.supportsServerFeatures()) {
                            try {
                                ServerFeatures features = electrumServer.getServerFeatures();
                                serverCapability.withServerFeatures(features);
                            } catch(ElectrumServerRpcException e) {
                                log.debug("Call to server.features failed for " + serverVersion, e);
                            }
                        }

                        if(isVerificationMandatory() && !serverCapability.supportsMerkleProofs()) {
                            throw new ServerException("Server does not support transaction verification (blockchain.transaction.get_merkle)");
                        }

                        BlockHeaderTip tip;
                        if(subscribe) {
                            tip = electrumServer.subscribeBlockHeaders();
                            String tipError = getTipValidationError(tip);
                            if(tipError != null) {
                                throw new ServerException(tipError);
                            }
                            if(isVerificationMandatory()) {
                                //A server below the last pinned header cannot serve the header sync, and would report every proof as refused
                                int maxCheckpointHeight = Network.get().getHeaderCheckpoints().getMaxHeight();
                                if(tip.height < maxCheckpointHeight) {
                                    throw new ServerException("Server is at height " + tip.height + ", below the last verified checkpoint at height " + maxCheckpointHeight);
                                }
                            }
                            initializeTip(tip);
                            subscribedScriptHashes.clear();
                        } else {
                            tip = new BlockHeaderTip();
                        }

                        String banner = electrumServer.getServerBanner();

                        Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(AppServices.TARGET_BLOCKS_RANGE, true);
                        Set<MempoolRateSize> mempoolRateSizes = electrumServer.getMempoolRateSizes();
                        feeRatesRetrievedAt = System.currentTimeMillis();

                        Double minimumRelayFeeRate = electrumServer.getMinimumRelayFee();
                        for(Integer blockTarget : blockTargetFeeRates.keySet()) {
                            blockTargetFeeRates.computeIfPresent(blockTarget, (blocks, feeRate) -> feeRate < minimumRelayFeeRate ? minimumRelayFeeRate : feeRate);
                        }

                        return new ConnectionEvent(serverVersion, banner, tip.height, tip.getBlockHeader(), blockTargetFeeRates, mempoolRateSizes, minimumRelayFeeRate);
                    } else {
                        if(reader.isAlive()) {
                            electrumServer.ping();
                            checkTipStaleness();

                            long elapsed = System.currentTimeMillis() - feeRatesRetrievedAt;
                            if(elapsed > FEE_RATES_PERIOD) {
                                Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(AppServices.TARGET_BLOCKS_RANGE, false);
                                Set<MempoolRateSize> mempoolRateSizes = electrumServer.getMempoolRateSizes();
                                Double nextBlockMedianFeeRate = electrumServer.getNextBlockMedianFeeRate();
                                feeRatesRetrievedAt = System.currentTimeMillis();
                                return new FeeRatesUpdatedEvent(blockTargetFeeRates, mempoolRateSizes, nextBlockMedianFeeRate);
                            }
                        } else {
                            closeConnection();
                        }
                    }

                    return null;
                }
            };
        }

        private void checkTipStaleness() {
            if(subscribe && Network.get() == Network.MAINNET && lastTipReceivedAt > 0 && !staleTipWarned && System.currentTimeMillis() - lastTipReceivedAt > STALE_TIP_WARNING_AGE_MILLIS) {
                staleTipWarned = true;
                long hours = (System.currentTimeMillis() - lastTipReceivedAt) / (60 * 60 * 1000);
                String warning = "Warning: The connected server has not announced a new block for over " + hours + " hours, so its chain view may be stale";
                log.warn(warning);
                Platform.runLater(() -> EventManager.get().post(new StatusEvent(warning, 120)));
            }
        }

        public void closeConnection() {
            try {
                closeActiveConnection();
                shutdown();
                firstCall = true;
            } catch (ServerException e) {
                log.error("Error closing connection", e);
            }
        }

        public boolean isConnecting() {
            return isRunning() && firstCall && !shutdown && (Config.get().getServerType() != ServerType.BITCOIN_CORE || (cormorant != null && !cormorant.isRunning()) || (bwt.isRunning() && !bwt.isReady()));
        }

        public boolean isConnectionRunning() {
            return isRunning() && (Config.get().getServerType() != ServerType.BITCOIN_CORE || bwt.isRunning());
        }

        public boolean isConnected() {
            return isRunning() && !firstCall && (Config.get().getServerType() != ServerType.BITCOIN_CORE || (cormorant != null && cormorant.isRunning()) || (bwt.isRunning() && bwt.isReady()));
        }

        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean cancel() {
            try {
                closeActiveConnection();
                shutdown();
            } catch (ServerException e) {
                log.error("Error closing connection", e);
            }

            return super.cancel();
        }

        private void shutdown() {
            shutdown = true;
            if(reader != null && reader.isAlive()) {
                reader.interrupt();
            }

            if(ElectrumServer.cormorant != null) {
                ElectrumServer.cormorant.stop();
                ElectrumServer.cormorant = null;
                ElectrumServer.coreElectrumServer = null;
            }

            if(Config.get().getServerType() == ServerType.BITCOIN_CORE && bwt.isRunning()) {
                Bwt.DisconnectionService disconnectionService = bwt.getDisconnectionService();
                disconnectionService.setOnSucceeded(workerStateEvent -> {
                    ElectrumServer.coreElectrumServer = null;
                    if(subscribe) {
                        EventManager.get().post(new BwtShutdownEvent());
                    }
                });
                disconnectionService.setOnFailed(workerStateEvent -> {
                    log.error("Failed to stop BWT", workerStateEvent.getSource().getException());
                });
                disconnectionService.start();
            } else if(subscribe) {
                Platform.runLater(() -> EventManager.get().post(new DisconnectionEvent()));
            }
        }

        @Override
        public void reset() {
            super.reset();
            firstCall = true;
            shutdown = false;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error("Uncaught error in ConnectionService", e);
        }

        @Subscribe
        public void bwtElectrumReadyStatus(BwtElectrumReadyStatusEvent event) {
            if(this.isRunning()) {
                ElectrumServer.coreElectrumServer = new Server(Protocol.TCP.toUrlString(HostAndPort.fromString(event.getElectrumAddr())));
            }
        }

        @Subscribe
        public void bwtReadyStatus(BwtReadyStatusEvent event) {
            if(this.isRunning()) {
                try {
                    bwtStartLock.lock();
                    bwtStartCondition.signal();
                } finally {
                    bwtStartLock.unlock();
                }
            }
        }

        @Subscribe
        public void bwtShutdown(BwtShutdownEvent event) {
            try {
                bwtStartLock.lock();
                bwtStartCondition.signal();
            } finally {
                bwtStartLock.unlock();
            }
        }

        @Subscribe
        public void mempoolEntriesInitialized(MempoolEntriesInitializedEvent event) throws ServerException {
            ElectrumServer electrumServer = new ElectrumServer();
            Set<MempoolRateSize> mempoolRateSizes = electrumServer.getMempoolRateSizes();
            EventManager.get().post(new MempoolRateSizesUpdatedEvent(mempoolRateSizes));
        }

        @Subscribe
        public void walletNodeHistoryChanged(WalletNodeHistoryChangedEvent event) {
            String status = broadcastRecent.remove(event.getScriptHash());
            if(status != null && status.equals(event.getStatus())) {
                Map<String, String> subscribeScriptHashes = new HashMap<>();
                Random random = new Random();
                int subscriptions = random.nextInt(2) + 1;
                for(int i = 0; i < subscriptions; i++) {
                    byte[] randomScriptHashBytes = new byte[32];
                    random.nextBytes(randomScriptHashBytes);
                    String randomScriptHash = Utils.bytesToHex(randomScriptHashBytes);
                    if(!subscribedScriptHashes.containsKey(randomScriptHash)) {
                        subscribeScriptHashes.put("m/" + subscribeScriptHashes.size(), randomScriptHash);
                    }
                }

                try {
                    electrumServerRpc.subscribeScriptHashes(transport, null, subscribeScriptHashes);
                    subscribeScriptHashes.values().forEach(scriptHash -> subscribedRecent.put(scriptHash, AppServices.getCurrentBlockHeight()));
                } catch(ElectrumServerRpcException e) {
                    log.debug("Error subscribing to recent mempool transaction outputs", e);
                }
            }

            BlockTransactionHash reference = getProofReference(event);
            if(reference != null) {
                TransactionProofService transactionProofService = new TransactionProofService(reference);
                transactionProofService.start();
            }
        }

        static BlockTransactionHash getProofReference(WalletNodeHistoryChangedEvent event) {
            BlockTransaction blkTx = confirmingRecent.get(event.getScriptHash());
            Integer currentHeight = AppServices.getCurrentBlockHeight();
            if(blkTx == null || currentHeight == null || !isVerifyingTransactions()) {
                return null;
            }

            String confirmedStatus = getScriptHashStatus(List.of(new ScriptHashTx(currentHeight, blkTx.getHashAsString(), blkTx.getFee())));
            if(!Objects.equals(confirmedStatus, event.getStatus())) {
                return null;
            }

            confirmingRecent.remove(event.getScriptHash());
            return new BlockTransaction(blkTx.getHash(), currentHeight, null, blkTx.getFee(), null);
        }
    }

    /**
     * Keeps the verified header store level with the chain tip. There is nothing to poll - the tip subscription pushes every new block - so this
     * service is event driven: it is restarted whenever a tip is announced, cancels itself once a run succeeds, and is cancelled on disconnection so
     * that a retry cannot open a transport of its own. Its period is therefore only the interval at which a failed run is retried.
     */
    public static class HeaderSyncService extends ScheduledService<Void> {
        public static final int RETRY_PERIOD_SECS = 60;

        //The pair from the event that last restarted this service: the height and the header of one announcement, never of two
        private volatile ChainTip announcedTip;

        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() throws Exception {
                    syncAnnouncedHeaders(announcedTip);
                    return null;
                }
            };
        }

        /**
         * The body of a run, which happens on a background thread: the connection check is therefore the transport level one, since AppServices reads
         * a JavaFX Service and may only be called from the application thread. Without the check a retry firing after the connection closed would
         * have getTransport() open a transport of its own, outside the connection lifecycle.
         */
        static void syncAnnouncedHeaders(ChainTip tip) throws ServerException {
            if(!isConnected()) {
                return;
            }

            ElectrumServer electrumServer = new ElectrumServer();
            try {
                electrumServer.syncHeaders(tip);
            } catch(UnsupportedMethodException e) {
                //Without this call the store can never advance, so verification would refuse every new confirmation for the rest of the session
                if(isVerificationMandatory()) {
                    //Leaving the capability on is what lets the next wallet history thread raise this and rotate the server, which this service cannot do
                    log.warn("Server does not support " + e.getMethod() + ", which is required to verify transactions");
                } else {
                    log.warn("Server does not support " + e.getMethod() + ", disabling transaction verification for this session");
                    serverCapability.withMerkleProofs(false);
                }
            }
        }

        @Subscribe
        public void connected(ConnectionEvent event) {
            if(!isVerifyingTransactions()) {
                return;
            }

            announcedTip = new ChainTip(event.getBlockHeight(), event.getBlockHeader());
            restart();
        }

        @Subscribe
        public void newBlock(NewBlockEvent event) {
            if(!isVerifyingTransactions()) {
                return;
            }

            //A header that extends the store, one that leaves a gap, and one that conflicts with it are all handled by the sync itself, which appends
            //an extending header without fetching anything, so there is nothing to classify here
            announcedTip = new ChainTip(event.getHeight(), event.getBlockHeader());
            restart();
        }

        @Subscribe
        public void disconnection(DisconnectionEvent event) {
            cancel();
        }
    }

    public static class ReadRunnable implements Runnable {
        @Override
        public void run() {
            try {
                TcpTransport tcpTransport = (TcpTransport)getTransport();
                tcpTransport.readInputLoop();
            } catch(ServerException e) {
                //Only debug logging here as the exception has been passed on to the ConnectionService thread via TcpTransport
                log.debug("Read thread terminated", e);
            }
        }
    }

    private static class WalletSyncLock {
        public boolean scriptHashesInitialized;
    }

    public static class TransactionHistoryService extends Service<Boolean> {
        private final Wallet mainWallet;
        private final List<Wallet> filterToWallets;
        private final Set<WalletNode> filterToNodes;

        public TransactionHistoryService(Wallet wallet) {
            this.mainWallet = wallet;
            this.filterToWallets = null;
            this.filterToNodes = null;
        }

        public TransactionHistoryService(Wallet mainWallet, List<Wallet> filterToWallets, Set<WalletNode> filterToNodes) {
            this.mainWallet = mainWallet;
            this.filterToWallets = filterToWallets;
            this.filterToNodes = filterToNodes;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ServerException {
                    if(ElectrumServer.cormorant != null) {
                        if(!ElectrumServer.cormorant.checkWalletImport(mainWallet)) {
                            return true;
                        }
                    }

                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.fetchAndCalculateHistory(mainWallet, filterToWallets, filterToNodes);
                }
            };
        }
    }

    public static class SilentPaymentScanService extends Service<Boolean> {
        private final Wallet wallet;
        private final SilentPaymentScanAddress scanAddress;
        private final boolean shouldHold;
        private final int neededStart;
        private volatile boolean releasedHold;

        public SilentPaymentScanService(Wallet wallet, boolean shouldHold, int neededStart) {
            this.wallet = wallet;
            this.scanAddress = wallet.getSilentPaymentScanAddress();
            this.shouldHold = shouldHold;
            this.neededStart = neededStart;
        }

        public boolean isReleasedHold() {
            return releasedHold;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                @Override
                protected Boolean call() throws ServerException {
                    boolean acquired = shouldHold || !ElectrumServer.hasSilentPaymentsCache(scanAddress);
                    if(acquired) {
                        ElectrumServer.holdSilentPaymentSubscription(wallet, scanAddress, neededStart);
                    }
                    try {
                        ElectrumServer electrumServer = new ElectrumServer();
                        List<SilentPaymentsTx> entries = electrumServer.getSilentPaymentHistory(scanAddress);
                        Set<WalletNode> affectedNodes = electrumServer.processSilentPaymentBatch(wallet, entries);

                        //Force affected nodes to be marked in subscribeWalletNodes regardless of whether
                        //their scripthash status push has arrived yet. Without this, a spent-input node
                        //whose push lags the SP-discovery channel won't be marked (its server-reported
                        //status still matches retrievedScriptHashes), and calculateNodeHistory won't run
                        //for it in this pass — leaving spentBy unset on the spent TXO.
                        for(WalletNode node : affectedNodes) {
                            clearRetrievedScriptHash(getScriptHash(node));
                        }

                        //First refresh (acquired): fetch all nodes to re-subscribe scripthashes the server forgot.
                        //Live delta: only the affected ones (newly-discovered SP nodes + nodes spent by the batch).
                        Set<WalletNode> nodesToFetch = acquired ? null : affectedNodes;
                        if(nodesToFetch != null && nodesToFetch.isEmpty()) {
                            return true;
                        }
                        return electrumServer.fetchAndCalculateHistory(wallet, null, nodesToFetch);
                    } catch(Exception e) {
                        if(acquired) {
                            ElectrumServer.releaseSilentPaymentSubscription(scanAddress);
                            releasedHold = true;
                        }
                        throw e;
                    }
                }
            };
        }
    }

    public static class SilentPaymentsUnsubscribeService extends Service<Boolean> {
        private final SilentPaymentScanAddress scanAddress;

        public SilentPaymentsUnsubscribeService(SilentPaymentScanAddress scanAddress) {
            this.scanAddress = scanAddress;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                @Override
                protected Boolean call() throws ServerException {
                    if(ElectrumServer.hasSilentPaymentsCache(scanAddress)) {
                        return false;
                    }
                    String scanPrivHex = Utils.bytesToHex(scanAddress.getScanKey().getPrivKeyBytes());
                    String spendPubHex = Utils.bytesToHex(scanAddress.getSpendKey().getPubKey(true));
                    electrumServerRpc.unsubscribeSilentPayments(getTransport(), scanPrivHex, spendPubHex);
                    return true;
                }
            };
        }
    }

    public static class TransactionMempoolService extends ScheduledService<Set<String>> {
        private final Wallet wallet;
        private final Sha256Hash txId;
        private final Set<WalletNode> nodes;
        private final IntegerProperty iterationCount = new SimpleIntegerProperty(0);
        private boolean cancelled;

        public TransactionMempoolService(Wallet wallet, Sha256Hash txId, Set<WalletNode> nodes) {
            this.wallet = wallet;
            this.txId = txId;
            this.nodes = nodes;
        }

        public int getIterationCount() {
            return iterationCount.get();
        }

        public IntegerProperty iterationCountProperty() {
            return iterationCount;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void start() {
            this.cancelled = false;
            super.start();
        }

        @Override
        public boolean cancel() {
            this.cancelled = true;
            return super.cancel();
        }

        @Override
        protected Task<Set<String>> createTask() {
            return new Task<>() {
                protected Set<String> call() throws ServerException {
                    if(ElectrumServer.cormorant != null) {
                        if(!ElectrumServer.cormorant.checkWalletImport(wallet)) {
                            return Collections.emptySet();
                        }
                    }

                    iterationCount.set(iterationCount.get() + 1);
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.getMempoolScriptHashes(wallet, txId, nodes);
                }
            };
        }
    }

    public static class TransactionReferenceService extends Service<Map<Sha256Hash, BlockTransaction>> {
        private final Set<Sha256Hash> references;
        private String scriptHash;

        public TransactionReferenceService(Transaction transaction) {
            references = new HashSet<>();
            references.add(transaction.getTxId());
            for(TransactionInput input : transaction.getInputs()) {
                references.add(input.getOutpoint().getHash());
            }
        }

        public TransactionReferenceService(Set<Sha256Hash> references, String scriptHash) {
            this(references);
            this.scriptHash = scriptHash;
        }

        public TransactionReferenceService(Set<Sha256Hash> references) {
            this.references = references;
        }

        @Override
        protected Task<Map<Sha256Hash, BlockTransaction>> createTask() {
            return new Task<>() {
                protected Map<Sha256Hash, BlockTransaction> call() throws ServerException {
                    Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
                    for(Sha256Hash ref : references) {
                        if(retrievedTransactions.containsKey(ref)) {
                            transactionMap.put(ref, retrievedTransactions.get(ref));
                        }
                    }

                    Set<Sha256Hash> fetchReferences = new HashSet<>(references);
                    fetchReferences.removeAll(transactionMap.keySet());

                    if(!fetchReferences.isEmpty()) {
                        ElectrumServer electrumServer = new ElectrumServer();
                        Map<Sha256Hash, BlockTransaction> fetchedTransactions = electrumServer.getReferencedTransactions(fetchReferences, scriptHash);
                        transactionMap.putAll(fetchedTransactions);

                        for(Map.Entry<Sha256Hash, BlockTransaction> fetchedEntry : fetchedTransactions.entrySet()) {
                            if(fetchedEntry.getValue() != null && !Sha256Hash.ZERO_HASH.equals(fetchedEntry.getValue().getBlockHash()) &&
                                    AppServices.getCurrentBlockHeight() != null && fetchedEntry.getValue().getConfirmations(AppServices.getCurrentBlockHeight()) >= BlockTransactionHash.BLOCKS_TO_CONFIRM) {
                                retrievedTransactions.put(fetchedEntry.getKey(), fetchedEntry.getValue());
                            }
                        }
                    }

                    return transactionMap;
                }
            };
        }
    }

    public static class TransactionOutputsReferenceService extends Service<List<BlockTransaction>> {
        private final Transaction transaction;
        private final int indexStart;
        private final int indexEnd;
        private final List<Set<BlockTransactionHash>> blockTransactionHashes;
        private final Map<Sha256Hash, BlockTransaction> transactionMap;

        public TransactionOutputsReferenceService(Transaction transaction, int indexStart, int indexEnd) {
            this.transaction = transaction;
            this.indexStart = Math.min(transaction.getOutputs().size(), indexStart);
            this.indexEnd = Math.min(transaction.getOutputs().size(), indexEnd);
            this.blockTransactionHashes = new ArrayList<>(transaction.getOutputs().size());
            for(int i = 0; i < transaction.getOutputs().size(); i++) {
                blockTransactionHashes.add(null);
            }
            this.transactionMap = new HashMap<>();
        }

        public TransactionOutputsReferenceService(Transaction transaction, int indexStart, int indexEnd, List<Set<BlockTransactionHash>> blockTransactionHashes, Map<Sha256Hash, BlockTransaction> transactionMap) {
            this.transaction = transaction;
            this.indexStart = Math.min(transaction.getOutputs().size(), indexStart);
            this.indexEnd = Math.min(transaction.getOutputs().size(), indexEnd);
            this.blockTransactionHashes = blockTransactionHashes;
            this.transactionMap = transactionMap;
        }

        @Override
        protected Task<List<BlockTransaction>> createTask() {
            return new Task<>() {
                protected List<BlockTransaction> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    List<Set<BlockTransactionHash>> outputTransactionReferences = electrumServer.getOutputTransactionReferences(transaction, indexStart, indexEnd, blockTransactionHashes);

                    Map<BlockTransactionHash, Transaction> setReferences = new HashMap<>();
                    for(Set<BlockTransactionHash> outputReferences : outputTransactionReferences) {
                        if(outputReferences != null) {
                            for(BlockTransactionHash outputReference : outputReferences) {
                                setReferences.put(outputReference, null);
                            }
                        }
                    }
                    setReferences.remove(null);
                    setReferences.remove(UNFETCHABLE_BLOCK_TRANSACTION);
                    setReferences.keySet().removeIf(ref -> transactionMap.get(ref.getHash()) != null);

                    List<BlockTransaction> blockTransactions = new ArrayList<>(transaction.getOutputs().size());
                    for(int i = 0; i < transaction.getOutputs().size(); i++) {
                        blockTransactions.add(null);
                    }

                    if(!setReferences.isEmpty()) {
                        Map<Integer, BlockHeader> blockHeaderMap = electrumServer.getBlockHeaders(null, setReferences.keySet());
                        transactionMap.putAll(electrumServer.getTransactions(null, setReferences, blockHeaderMap));
                    }

                    for(int i = 0; i < outputTransactionReferences.size(); i++) {
                        Set<BlockTransactionHash> outputReferences = outputTransactionReferences.get(i);
                        if(outputReferences != null) {
                            for(BlockTransactionHash reference : outputReferences) {
                                if(reference == UNFETCHABLE_BLOCK_TRANSACTION) {
                                    if(blockTransactions.get(i) == null) {
                                        blockTransactions.set(i, UNFETCHABLE_BLOCK_TRANSACTION);
                                    }
                                } else {
                                    BlockTransaction blockTransaction = transactionMap.get(reference.getHash());
                                    if(blockTransaction.equals(UNFETCHABLE_BLOCK_TRANSACTION)) {
                                        if(blockTransactions.get(i) == null) {
                                            blockTransactions.set(i, UNFETCHABLE_BLOCK_TRANSACTION);
                                        }
                                    } else {
                                        for(TransactionInput input : blockTransaction.getTransaction().getInputs()) {
                                            if(input.getOutpoint().getHash().equals(transaction.getTxId()) && input.getOutpoint().getIndex() == i) {
                                                BlockTransaction previousTx = blockTransactions.set(i, blockTransaction);
                                                if(previousTx != null && !previousTx.equals(UNFETCHABLE_BLOCK_TRANSACTION)) {
                                                    throw new IllegalStateException("Double spend detected for output #" + i + " on hash " + reference.getHash());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return blockTransactions;
                }
            };
        }
    }

    public static class TransactionProofService extends Service<Map<String, TransactionMerkleProof>> {
        private final BlockTransactionHash reference;

        public TransactionProofService(BlockTransactionHash reference) {
            this.reference = reference;
        }

        @Override
        protected Task<Map<String, TransactionMerkleProof>> createTask() {
            return new Task<>() {
                @Override
                protected Map<String, TransactionMerkleProof> call() {
                    try {
                        //getTransport() opens one where there is none, so a task running after the connection closed must not reach it
                        if(isConnected()) {
                            return electrumServerRpc.getTransactionMerkleProofs(getTransport(), null, List.of(reference));
                        }
                    } catch(Exception e) {
                        log.debug("Error retrieving proof for transaction", e);
                    }

                    return Collections.emptyMap();
                }
            };
        }
    }

    public static class BroadcastTransactionService extends Service<Sha256Hash> {
        private final Transaction transaction;
        private final Long fee;

        public BroadcastTransactionService(Transaction transaction, Long fee) {
            this.transaction = transaction;
            this.fee = fee;
        }

        @Override
        protected Task<Sha256Hash> createTask() {
            return new Task<>() {
                protected Sha256Hash call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.broadcastTransaction(transaction, fee);
                }
            };
        }
    }

    public static class FeeRatesService extends Service<FeeRatesUpdatedEvent> {
        @Override
        protected Task<FeeRatesUpdatedEvent> createTask() {
            return new Task<>() {
                protected FeeRatesUpdatedEvent call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(AppServices.TARGET_BLOCKS_RANGE, false);
                    Double nextBlockMedianFeeRate = electrumServer.getNextBlockMedianFeeRate();
                    return new FeeRatesUpdatedEvent(blockTargetFeeRates, null, nextBlockMedianFeeRate);
                }
            };
        }
    }

    public static class BlockSummaryService extends Service<BlockSummaryEvent> {
        private final List<NewBlockEvent> newBlockEvents;

        public BlockSummaryService(List<NewBlockEvent> newBlockEvents) {
            this.newBlockEvents = newBlockEvents;
        }

        @Override
        protected Task<BlockSummaryEvent> createTask() {
            return new Task<>() {
                protected BlockSummaryEvent call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    Map<Integer, BlockSummary> blockSummaryMap = new LinkedHashMap<>();

                    int maxHeight = AppServices.getBlockSummaries().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                    int startHeight = newBlockEvents.stream().mapToInt(NewBlockEvent::getHeight).min().orElse(0);
                    int endHeight = newBlockEvents.stream().mapToInt(NewBlockEvent::getHeight).max().orElse(0);
                    int totalBlocks = Math.max(0, endHeight - maxHeight);

                    if(startHeight == 0 || totalBlocks > 1 || startHeight > maxHeight + 1) {
                        if(isBlockstorm(totalBlocks)) {
                            int start = Math.max(maxHeight + 1, endHeight - 15);
                            for(int height = start; height <= endHeight; height++) {
                                blockSummaryMap.put(height, new BlockSummary(height, new Date(), 1.0d, 0, 0));
                            }
                        } else {
                            blockSummaryMap.putAll(electrumServer.getRecentBlockSummaryMap());
                        }
                    }

                    List<NewBlockEvent> events = new ArrayList<>(newBlockEvents);
                    events.removeIf(event -> blockSummaryMap.containsKey(event.getHeight()));
                    if(!events.isEmpty()) {
                        for(NewBlockEvent event : newBlockEvents) {
                            blockSummaryMap.putAll(electrumServer.getBlockSummaryMap(event.getHeight(), event.getBlockHeader()));
                        }
                    }

                    Config config = Config.get();
                    if(!isBlockstorm(totalBlocks) && !AppServices.isUsingProxy() && config.getServer().getProtocol().equals(Protocol.SSL)
                            && (config.getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER || config.getServerType() == ServerType.ELECTRUM_SERVER)) {
                        subscribeRecent(electrumServer, AppServices.getCurrentBlockHeight() == null ? endHeight : AppServices.getCurrentBlockHeight());
                    }

                    Double nextBlockMedianFeeRate = null;
                    if(!isBlockstorm(totalBlocks)) {
                        nextBlockMedianFeeRate = electrumServer.getNextBlockMedianFeeRate();
                    }
                    return new BlockSummaryEvent(blockSummaryMap, nextBlockMedianFeeRate);
                }
            };
        }

        private boolean isBlockstorm(int totalBlocks) {
            return Network.get() != Network.MAINNET && totalBlocks > 2;
        }

        private void subscribeRecent(ElectrumServer electrumServer, int currentHeight) {
            Set<String> unsubscribeScriptHashes = subscribedRecent.entrySet().stream().filter(entry -> entry.getValue() == null || entry.getValue() <= currentHeight - 3)
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
            unsubscribeScriptHashes.removeIf(subscribedScriptHashes::containsKey);
            if(!unsubscribeScriptHashes.isEmpty() && serverCapability.supportsUnsubscribe()) {
                electrumServerRpc.unsubscribeScriptHashes(transport, unsubscribeScriptHashes);
            }
            subscribedRecent.keySet().removeAll(unsubscribeScriptHashes);
            broadcastRecent.keySet().removeAll(unsubscribeScriptHashes);
            confirmingRecent.keySet().removeAll(unsubscribeScriptHashes);

            Map<String, String> subscribeScriptHashes = new HashMap<>();
            Map<String, BlockTransaction> confirming = new HashMap<>();
            List<BlockTransaction> recentTransactions = electrumServer.getRecentMempoolTransactions();
            for(BlockTransaction blkTx : recentTransactions) {
                for(int i = 0; i < blkTx.getTransaction().getOutputs().size(); i++) {
                    TransactionOutput txOutput = blkTx.getTransaction().getOutputs().get(i);
                    String scriptHash = getScriptHash(txOutput);
                    if(!subscribedScriptHashes.containsKey(scriptHash)) {
                        subscribeScriptHashes.put("m/" + subscribeScriptHashes.size(), scriptHash);
                        confirming.put(scriptHash, blkTx);
                    }
                    if(Math.random() < 0.1d) {
                        break;
                    }
                }
            }

            if(!subscribeScriptHashes.isEmpty()) {
                Random random = new Random();
                int additionalRandomScriptHashes = random.nextInt(8);
                for(int i = 0; i < additionalRandomScriptHashes; i++) {
                    byte[] randomScriptHashBytes = new byte[32];
                    random.nextBytes(randomScriptHashBytes);
                    String randomScriptHash = Utils.bytesToHex(randomScriptHashBytes);
                    if(!subscribedScriptHashes.containsKey(randomScriptHash)) {
                        subscribeScriptHashes.put("m/" + subscribeScriptHashes.size(), randomScriptHash);
                    }
                }

                try {
                    electrumServerRpc.subscribeScriptHashes(transport, null, subscribeScriptHashes);
                    subscribeScriptHashes.values().forEach(scriptHash -> subscribedRecent.put(scriptHash, currentHeight));
                    confirmingRecent.putAll(confirming);
                } catch(ElectrumServerRpcException e) {
                    log.debug("Error subscribing to recent mempool transactions", e);
                }
            }

            if(!recentTransactions.isEmpty()) {
                broadcastRecent(electrumServer, recentTransactions);
            }
        }

        private void broadcastRecent(ElectrumServer electrumServer, List<BlockTransaction> recentTransactions) {
            ScheduledService<Void> broadcastService = new ScheduledService<>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<>() {
                        @Override
                        protected Void call() throws Exception {
                            if(!recentTransactions.isEmpty()) {
                                Random random = new Random();
                                if(random.nextBoolean()) {
                                    BlockTransaction blkTx = recentTransactions.get(random.nextInt(recentTransactions.size()));
                                    String scriptHash = getScriptHash(blkTx.getTransaction().getOutputs().getFirst());
                                    String status = getScriptHashStatus(List.of(new ScriptHashTx(0, blkTx.getHashAsString(), blkTx.getFee())));
                                    broadcastRecent.put(scriptHash, status);
                                    electrumServer.broadcastTransaction(blkTx.getTransaction());
                                }
                            }
                            return null;
                        }
                    };
                }
            };
            broadcastService.setDelay(Duration.seconds(Math.random() * 60 * 10));
            broadcastService.setPeriod(Duration.hours(1));
            broadcastService.setOnSucceeded(_ -> broadcastService.cancel());
            broadcastService.setOnFailed(_ -> broadcastService.cancel());
            broadcastService.start();
        }
    }

    public static class WalletDiscoveryService extends Service<Optional<List<Wallet>>> {
        private final List<Wallet> wallets;

        public WalletDiscoveryService(List<Wallet> wallets) {
            this.wallets = wallets;
        }

        @Override
        protected Task<Optional<List<Wallet>>> createTask() {
            return new Task<>() {
                protected Optional<List<Wallet>> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();

                    List<Wallet> discoveredWallets = new ArrayList<>();
                    for(int i = 0; i < wallets.size(); i++) {
                        Wallet wallet = wallets.get(i);
                        updateProgress(i, wallets.size() + StandardAccount.DISCOVERY_ACCOUNTS.size());
                        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new TreeMap<>();
                        electrumServer.getReferences(wallet, wallet.getNode(KeyPurpose.RECEIVE).getChildren(), nodeTransactionMap, 0);
                        boolean found = nodeTransactionMap.values().stream().anyMatch(blockTransactionHashes -> !blockTransactionHashes.isEmpty());

                        for(Iterator<Wallet> iterator = wallet.getChildWallets().iterator(); iterator.hasNext(); ) {
                            Wallet childWallet = iterator.next();
                            Map<WalletNode, Set<BlockTransactionHash>> childTransactionMap = new TreeMap<>();
                            electrumServer.getReferences(childWallet, childWallet.getNode(KeyPurpose.RECEIVE).getChildren(), childTransactionMap, 0);
                            if(childTransactionMap.values().stream().anyMatch(blockTransactionHashes -> !blockTransactionHashes.isEmpty())) {
                                found = true;
                            } else {
                                iterator.remove();
                            }
                        }

                        if(found) {
                            Wallet masterWalletCopy = wallet.copy();
                            List<StandardAccount> searchAccounts = getStandardAccounts(wallet);
                            Set<StandardAccount> foundAccounts = new LinkedHashSet<>();
                            for(int j = 0; j < searchAccounts.size(); j++) {
                                StandardAccount standardAccount = searchAccounts.get(j);
                                Wallet childWallet = masterWalletCopy.addChildWallet(standardAccount);
                                Map<WalletNode, Set<BlockTransactionHash>> childTransactionMap = new TreeMap<>();
                                electrumServer.getReferences(childWallet, childWallet.getNode(KeyPurpose.RECEIVE).getChildren(), childTransactionMap, 0);
                                if(childTransactionMap.values().stream().anyMatch(blockTransactionHashes -> !blockTransactionHashes.isEmpty())) {
                                    if(StandardAccount.isWhirlpoolAccount(standardAccount)) {
                                        foundAccounts.addAll(StandardAccount.WHIRLPOOL_ACCOUNTS);
                                    } else {
                                        foundAccounts.add(standardAccount);
                                    }
                                }
                                updateProgress(i + j, wallets.size() + StandardAccount.DISCOVERY_ACCOUNTS.size());
                            }

                            for(StandardAccount standardAccount : foundAccounts) {
                                wallet.addChildWallet(standardAccount);
                            }

                            discoveredWallets.add(wallet);
                        }
                    }

                    return discoveredWallets.isEmpty() ? Optional.empty() : Optional.of(discoveredWallets);
                }
            };
        }

        private List<StandardAccount> getStandardAccounts(Wallet wallet) {
            if(!wallet.getKeystores().stream().allMatch(Keystore::hasMasterPrivateKey)) {
                return Collections.emptyList();
            }

            List<StandardAccount> accounts = new ArrayList<>();
            for(StandardAccount account : StandardAccount.DISCOVERY_ACCOUNTS) {
                if(account != StandardAccount.ACCOUNT_0 && (!StandardAccount.isWhirlpoolAccount(account) || wallet.getScriptType() == ScriptType.P2WPKH)) {
                    accounts.add(account);
                }
            }

            return accounts;
        }
    }

    public static class AccountDiscoveryService extends Service<List<StandardAccount>> {
        private final Wallet masterWalletCopy;
        private final List<StandardAccount> standardAccounts;
        private final Map<StandardAccount, Keystore> importedKeystores;

        public AccountDiscoveryService(Wallet masterWallet, List<StandardAccount> standardAccounts) {
            this.masterWalletCopy = masterWallet.copy();
            this.standardAccounts = standardAccounts;
            this.importedKeystores = new HashMap<>();
        }

        public AccountDiscoveryService(Wallet masterWallet, Map<StandardAccount, Keystore> importedKeystores) {
            this.masterWalletCopy = masterWallet.copy();
            this.standardAccounts = new ArrayList<>(importedKeystores.keySet());
            this.importedKeystores = importedKeystores;
        }

        @Override
        protected Task<List<StandardAccount>> createTask() {
            return new Task<>() {
                protected List<StandardAccount> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    List<StandardAccount> discoveredAccounts = new ArrayList<>();

                    for(StandardAccount standardAccount : standardAccounts) {
                        Wallet wallet = masterWalletCopy.addChildWallet(standardAccount);
                        if(importedKeystores.containsKey(standardAccount)) {
                            wallet.getKeystores().clear();
                            wallet.getKeystores().add(importedKeystores.get(standardAccount));
                        }

                        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new TreeMap<>();
                        electrumServer.getReferences(wallet, wallet.getNode(KeyPurpose.RECEIVE).getChildren(), nodeTransactionMap, 0);
                        if(nodeTransactionMap.values().stream().anyMatch(blockTransactionHashes -> !blockTransactionHashes.isEmpty())) {
                            discoveredAccounts.add(standardAccount);
                        }
                    }

                    return discoveredAccounts;
                }
            };
        }
    }

    public static class AddressUtxosService extends Service<List<TransactionOutput>> {
        private final Address address;
        private final Date since;

        public AddressUtxosService(Address address, Date since) {
            this.address = address;
            this.since = since;
        }

        @Override
        protected Task<List<TransactionOutput>> createTask() {
            return new Task<>() {
                protected List<TransactionOutput> call() throws ServerException {
                    if(ElectrumServer.cormorant != null) {
                        updateProgress(-1, 0);
                        ElectrumServer.cormorant.checkAddressImport(address, since);
                    }

                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.getUtxos(address);
                }
            };
        }
    }

    public static class PaymentCodesService extends Service<List<Wallet>> {
        private final String walletId;
        private final Wallet wallet;

        public PaymentCodesService(String walletId, Wallet wallet) {
            this.walletId = walletId;
            this.wallet = wallet;
        }

        @Override
        protected Task<List<Wallet>> createTask() {
            return new Task<>() {
                protected List<Wallet> call() throws ServerException {
                    if(ElectrumServer.cormorant != null) {
                        if(!ElectrumServer.cormorant.checkWalletImport(wallet)) {
                            return Collections.emptyList();
                        }
                    }

                    Wallet notificationWallet = wallet.getNotificationWallet();
                    WalletNode notificationNode = notificationWallet.getNode(KeyPurpose.NOTIFICATION);

                    for(Wallet childWallet : wallet.getChildWallets()) {
                        if(childWallet.isBip47()) {
                            WalletNode savedNotificationNode = childWallet.getNode(KeyPurpose.NOTIFICATION);
                            notificationNode.getTransactionOutputs().addAll(savedNotificationNode.getTransactionOutputs());
                            notificationWallet.updateTransactions(childWallet.getTransactions());
                        }
                    }

                    addCalculatedScriptHashes(notificationNode);

                    ElectrumServer electrumServer = new ElectrumServer();
                    Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap;
                    try {
                        nodeTransactionMap = electrumServer.getHistory(notificationWallet, List.of(notificationNode));
                        electrumServer.getReferencedTransactions(notificationWallet, nodeTransactionMap);
                        electrumServer.calculateNodeHistory(notificationWallet, nodeTransactionMap);
                    } finally {
                        //The notification wallet is derived rather than opened, so what it could not prove is reported against the wallet it belongs to
                        electrumServer.postProofEvents(notificationWallet, wallet);
                    }

                    List<Wallet> addedWallets = new ArrayList<>();
                    if(!nodeTransactionMap.isEmpty()) {
                        Set<PaymentCode> paymentCodes = new LinkedHashSet<>();
                        for(BlockTransactionHashIndex output : notificationNode.getTransactionOutputs()) {
                            BlockTransaction blkTx = notificationWallet.getTransactions().get(output.getHash());
                            try {
                                PaymentCode paymentCode = PaymentCode.getPaymentCode(blkTx.getTransaction(), notificationWallet.getKeystores().get(0));
                                if(paymentCodes.add(paymentCode)) {
                                    if(getExistingChildWallet(paymentCode) == null) {
                                        PayNym payNym = Config.get().isUsePayNym() ? getPayNym(paymentCode) : null;
                                        List<ScriptType> scriptTypes = payNym == null || wallet.getScriptType() != ScriptType.P2PKH ? PayNym.getSegwitScriptTypes() : payNym.getScriptTypes();
                                        for(ScriptType childScriptType : scriptTypes) {
                                            String label = (payNym == null ? paymentCode.toAbbreviatedString() : payNym.nymName()) + " " + childScriptType.getName();
                                            Wallet addedWallet = wallet.addChildWallet(paymentCode, childScriptType, output, blkTx, label);
                                            //Check this is a valid payment code, will throw IllegalArgumentException if not
                                            try {
                                                WalletNode receiveNode = new WalletNode(addedWallet, KeyPurpose.RECEIVE, 0);
                                                receiveNode.getPubKey();
                                            } catch(IllegalArgumentException e) {
                                                wallet.getChildWallets().remove(addedWallet);
                                                throw e;
                                            }

                                            addedWallets.add(addedWallet);
                                        }
                                    }
                                }
                            } catch(InvalidPaymentCodeException e) {
                                log.info("Could not determine payment code for notification transaction", e);
                            } catch(IllegalArgumentException e) {
                                log.info("Invalid notification transaction creates illegal payment code", e);
                            }
                        }
                    }

                    return addedWallets;
                }
            };
        }

        private PayNym getPayNym(PaymentCode paymentCode) {
            try {
                return PayNymService.getPayNym(paymentCode.toString()).blockingFirst();
            } catch(Exception e) {
                //ignore
            }

            return null;
        }

        private Wallet getExistingChildWallet(PaymentCode paymentCode) {
            for(Wallet childWallet : wallet.getChildWallets()) {
                if(childWallet.isBip47() && paymentCode.equals(childWallet.getKeystores().get(0).getExternalPaymentCode())) {
                    return childWallet;
                }
            }

            return null;
        }
    }
}
