package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.bip47.InvalidPaymentCodeException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.soroban.PayNym;
import com.sparrowwallet.sparrow.soroban.Soroban;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ElectrumServer {
    private static final Logger log = LoggerFactory.getLogger(ElectrumServer.class);

    private static final String[] SUPPORTED_VERSIONS = new String[]{"1.3", "1.4.2"};

    private static final Version ELECTRS_MIN_BATCHING_VERSION = new Version("0.9.0");

    private static final Version FULCRUM_MIN_BATCHING_VERSION = new Version("1.6.0");

    private static final int MINIMUM_BROADCASTS = 2;

    public static final BlockTransaction UNFETCHABLE_BLOCK_TRANSACTION = new BlockTransaction(Sha256Hash.ZERO_HASH, 0, null, null, null);

    private static Transport transport;

    private static final Map<String, List<String>> subscribedScriptHashes = Collections.synchronizedMap(new HashMap<>());

    private static String previousServerAddress;

    private static Map<String, String> retrievedScriptHashes = Collections.synchronizedMap(new HashMap<>());

    private static Map<Sha256Hash, BlockTransaction> retrievedTransactions = Collections.synchronizedMap(new HashMap<>());

    private static ElectrumServerRpc electrumServerRpc = new SimpleElectrumServerRpc();

    private static String bwtElectrumServer;

    private static final Pattern RPC_WALLET_LOADING_PATTERN = Pattern.compile(".*\"(Wallet loading failed:[^\"]*)\".*");

    private static synchronized Transport getTransport() throws ServerException {
        if(transport == null) {
            try {
                String electrumServer = null;
                File electrumServerCert = null;
                String proxyServer = null;

                if(Config.get().getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER) {
                    electrumServer = Config.get().getPublicElectrumServer();
                    proxyServer = Config.get().getProxyServer();
                } else if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
                    if(bwtElectrumServer == null) {
                        throw new ServerConfigException("Could not connect to Bitcoin Core RPC");
                    }
                    electrumServer = bwtElectrumServer;
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

                Protocol protocol = Protocol.getProtocol(electrumServer);
                if(protocol == null) {
                    throw new ServerConfigException("Electrum server URL must start with " + Protocol.TCP.toUrlString() + " or " + Protocol.SSL.toUrlString());
                }

                //If changing server, don't rely on previous transaction history
                if(previousServerAddress != null && !electrumServer.equals(previousServerAddress)) {
                    retrievedScriptHashes.clear();
                    retrievedTransactions.clear();
                }
                previousServerAddress = electrumServer;

                HostAndPort server = protocol.getServerHostAndPort(electrumServer);
                boolean localNetworkAddress = !protocol.isOnionAddress(server) && IpAddressMatcher.isLocalNetworkAddress(server.getHost());

                if(!localNetworkAddress && Config.get().isUseProxy() && proxyServer != null && !proxyServer.isBlank()) {
                    HostAndPort proxy = HostAndPort.fromString(proxyServer);
                    if(electrumServerCert != null) {
                        transport = protocol.getTransport(server, electrumServerCert, proxy);
                    } else {
                        transport = protocol.getTransport(server, proxy);
                    }
                } else {
                    if(electrumServerCert != null) {
                        transport = protocol.getTransport(server, electrumServerCert);
                    } else {
                        transport = protocol.getTransport(server);
                    }
                }
            } catch (Exception e) {
                throw new ServerConfigException(e);
            }
        }

        return transport;
    }

    public void connect() throws ServerException {
        TcpTransport tcpTransport = (TcpTransport)getTransport();
        tcpTransport.connect();
    }

    public void ping() throws ServerException {
        electrumServerRpc.ping(getTransport());
    }

    public List<String> getServerVersion() throws ServerException {
        return electrumServerRpc.getServerVersion(getTransport(), "Sparrow", SUPPORTED_VERSIONS);
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
        try {
            if(transport != null) {
                Closeable closeableTransport = (Closeable)transport;
                closeableTransport.close();
                transport = null;
            }
        } catch (IOException e) {
            throw new ServerException(e);
        }
    }

    private static void addCalculatedScriptHashes(Wallet wallet) {
        getCalculatedScriptHashes(wallet).forEach(retrievedScriptHashes::putIfAbsent);
    }

    private static void addCalculatedScriptHashes(Wallet wallet, WalletNode walletNode) {
        Map<String, String> calculatedScriptHashStatuses = new HashMap<>();
        addScriptHashStatus(calculatedScriptHashStatuses, wallet, walletNode);
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
            addScriptHashStatus(calculatedScriptHashes, wallet, walletNode);
        }

        return calculatedScriptHashes;
    }

    private static void addScriptHashStatus(Map<String, String> calculatedScriptHashes, Wallet wallet, WalletNode walletNode) {
        String scriptHash = getScriptHash(wallet, walletNode);
        String scriptHashStatus = getScriptHashStatus(walletNode);
        calculatedScriptHashes.put(scriptHash, scriptHashStatus);
    }

    private static String getScriptHashStatus(WalletNode walletNode) {
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
            return 0;
        });
        if(!txos.isEmpty()) {
            StringBuilder scriptHashStatus = new StringBuilder();
            for(BlockTransactionHashIndex txo : txos) {
                scriptHashStatus.append(txo.getHash().toString()).append(":").append(txo.getHeight()).append(":");
            }

            return Utils.bytesToHex(Sha256Hash.hash(scriptHashStatus.toString().getBytes(StandardCharsets.UTF_8)));
        } else {
            return null;
        }
    }

    public static void clearRetrievedScriptHashes(Wallet wallet) {
        wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().map(node -> getScriptHash(wallet, node)).forEach(scriptHash -> retrievedScriptHashes.remove(scriptHash));
        wallet.getNode(KeyPurpose.CHANGE).getChildren().stream().map(node -> getScriptHash(wallet, node)).forEach(scriptHash -> retrievedScriptHashes.remove(scriptHash));
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
        getAddressNodes(wallet, purposeNode).stream().filter(node -> !nodeTransactionMap.containsKey(node) && retrievedScriptHashes.get(getScriptHash(wallet, node)) == null).forEach(node -> nodeTransactionMap.put(node, Collections.emptySet()));
    }

    private void getHistoryToGapLimit(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, WalletNode purposeNode) throws ServerException {
        //Because node children are added sequentially in WalletNode.fillToIndex, we can simply look at the number of children to determine the highest filled index
        int historySize = purposeNode.getChildren().size();
        //The gap limit size takes the highest used index in the retrieved history and adds the gap limit (plus one to be comparable to the number of children since index is zero based)
        int gapLimitSize = getGapLimitSize(wallet, nodeTransactionMap);
        while(historySize < gapLimitSize) {
            purposeNode.fillToIndex(wallet, gapLimitSize - 1);
            subscribeWalletNodes(wallet, getAddressNodes(wallet, purposeNode), nodeTransactionMap, historySize);
            getReferences(wallet, nodeTransactionMap.keySet(), nodeTransactionMap, historySize);
            getReferencedTransactions(wallet, nodeTransactionMap);
            historySize = purposeNode.getChildren().size();
            gapLimitSize = getGapLimitSize(wallet, nodeTransactionMap);
        }
    }

    private Set<WalletNode> getAddressNodes(Wallet wallet, WalletNode purposeNode) {
        Integer watchLast = wallet.getWatchLast();
        if(watchLast == null || watchLast < wallet.getGapLimit() || wallet.getStoredBlockHeight() == 0 || wallet.getTransactions().isEmpty()) {
            return purposeNode.getChildren();
        }

        int highestUsedIndex = purposeNode.getChildren().stream().filter(WalletNode::isUsed).mapToInt(WalletNode::getIndex).max().orElse(0);
        int startFromIndex = highestUsedIndex - watchLast;
        return purposeNode.getChildren().stream().filter(walletNode -> walletNode.getIndex() >= startFromIndex).collect(Collectors.toCollection(TreeSet::new));
    }

    private int getGapLimitSize(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) {
        int highestIndex = nodeTransactionMap.keySet().stream().map(WalletNode::getIndex).max(Comparator.comparing(Integer::valueOf)).orElse(-1);
        return highestIndex + wallet.getGapLimit() + 1;
    }

    public void getReferences(Wallet wallet, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, int startIndex) throws ServerException {
        try {
            Map<String, String> pathScriptHashes = new LinkedHashMap<>(nodes.size());
            for(WalletNode node : nodes) {
                if(node.getIndex() >= startIndex) {
                    pathScriptHashes.put(node.getDerivationPath(), getScriptHash(wallet, node));
                }
            }

            if(pathScriptHashes.isEmpty()) {
                return;
            }

            //Even if we have some successes, failure to retrieve all references will result in an incomplete wallet history. Don't proceed if that's the case.
            Map<String, ScriptHashTx[]> result = electrumServerRpc.getScriptHashHistory(getTransport(), wallet, pathScriptHashes, true);

            for(String path : result.keySet()) {
                ScriptHashTx[] txes = result.get(path);

                Optional<WalletNode> optionalNode = nodes.stream().filter(n -> n.getDerivationPath().equals(path)).findFirst();
                if(optionalNode.isPresent()) {
                    WalletNode node = optionalNode.get();

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
            for(WalletNode node : nodes) {
                if(node == null) {
                    log.error("Null node for wallet " + wallet.getFullName() + " subscribing nodes " + nodes + " startIndex " + startIndex, new Throwable());
                }

                if(node != null && node.getIndex() >= startIndex) {
                    String scriptHash = getScriptHash(wallet, node);
                    String subscribedStatus = getSubscribedScriptHashStatus(scriptHash);
                    if(subscribedStatus != null) {
                        //Already subscribed, but still need to fetch history from a used node if not previously fetched or present
                        if(!subscribedStatus.equals(retrievedScriptHashes.get(scriptHash)) || !subscribedStatus.equals(getScriptHashStatus(node))) {
                            nodeTransactionMap.put(node, new TreeSet<>());
                        }
                    } else if(!subscribedScriptHashes.containsKey(scriptHash) && scriptHashes.add(scriptHash)) {
                        //Unique script hash we are not yet subscribed to
                        pathScriptHashes.put(node.getDerivationPath(), scriptHash);
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

                Optional<WalletNode> optionalNode = nodes.stream().filter(n -> n.getDerivationPath().equals(path)).findFirst();
                if(optionalNode.isPresent()) {
                    WalletNode node = optionalNode.get();
                    String scriptHash = getScriptHash(wallet, node);

                    //Check if there is history for this script hash, and if the history has changed since last fetched
                    if(status != null && !status.equals(retrievedScriptHashes.get(scriptHash))) {
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

    public List<Set<BlockTransactionHash>> getOutputTransactionReferences(Transaction transaction, int indexStart, int indexEnd) throws ServerException {
        try {
            Map<String, String> pathScriptHashes = new LinkedHashMap<>();
            for(int i = indexStart; i < transaction.getOutputs().size() && i < indexEnd; i++) {
                TransactionOutput output = transaction.getOutputs().get(i);
                pathScriptHashes.put(Integer.toString(i), getScriptHash(output));
            }

            Map<String, ScriptHashTx[]> result = electrumServerRpc.getScriptHashHistory(getTransport(), null, pathScriptHashes, false);

            List<Set<BlockTransactionHash>> blockTransactionHashes = new ArrayList<>(transaction.getOutputs().size());
            for(int i = 0; i < transaction.getOutputs().size(); i++) {
                blockTransactionHashes.add(null);
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
        Set<BlockTransactionHash> references = new TreeSet<>();
        for(Set<BlockTransactionHash> nodeReferences : nodeTransactionMap.values()) {
            references.addAll(nodeReferences);
        }

        for(Iterator<BlockTransactionHash> iter = references.iterator(); iter.hasNext(); ) {
            BlockTransactionHash reference = iter.next();
            BlockTransaction blockTransaction = wallet.getTransactions().get(reference.getHash());
            if(blockTransaction != null && reference.getHeight() == blockTransaction.getHeight()) {
                iter.remove();
            }
        }

        Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
        if(!references.isEmpty()) {
            Map<Integer, BlockHeader> blockHeaderMap = getBlockHeaders(wallet, references);
            transactionMap = getTransactions(wallet, references, blockHeaderMap);
        }

        if(!transactionMap.equals(wallet.getTransactions())) {
            wallet.updateTransactions(transactionMap);
        }
    }

    public Map<Integer, BlockHeader> getBlockHeaders(Wallet wallet, Set<BlockTransactionHash> references) throws ServerException {
        try {
            Set<Integer> blockHeights = new TreeSet<>();
            for(BlockTransactionHash reference : references) {
                if(reference.getHeight() > 0) {
                    blockHeights.add(reference.getHeight());
                }
            }

            if(blockHeights.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<Integer, String> result = electrumServerRpc.getBlockHeaders(getTransport(), wallet, blockHeights);

            Map<Integer, BlockHeader> blockHeaderMap = new TreeMap<>();
            for(Integer height : result.keySet()) {
                byte[] blockHeaderBytes = Utils.hexToBytes(result.get(height));
                BlockHeader blockHeader = new BlockHeader(blockHeaderBytes);
                blockHeaderMap.put(height, blockHeader);
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

    public Map<Sha256Hash, BlockTransaction> getTransactions(Wallet wallet, Set<BlockTransactionHash> references, Map<Integer, BlockHeader> blockHeaderMap) throws ServerException {
        try {
            Set<BlockTransactionHash> checkReferences = new TreeSet<>(references);

            Set<String> txids = new LinkedHashSet<>(references.size());
            for(BlockTransactionHash reference : references) {
                txids.add(reference.getHashAsString());
            }

            Map<String, String> result = electrumServerRpc.getTransactions(getTransport(), wallet, txids);

            String strErrorTx = Sha256Hash.ZERO_HASH.toString();
            Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
            for(String txid : result.keySet()) {
                Sha256Hash hash = Sha256Hash.wrap(txid);
                String strRawTx = result.get(txid);

                if(strRawTx.equals(strErrorTx)) {
                    transactionMap.put(hash, UNFETCHABLE_BLOCK_TRANSACTION);
                    checkReferences.removeIf(ref -> ref.getHash().equals(hash));
                    continue;
                }

                byte[] rawtx = Utils.hexToBytes(strRawTx);
                Transaction transaction;

                try {
                    transaction = new Transaction(rawtx);
                } catch(ProtocolException e) {
                    log.error("Could not parse tx: " + strRawTx);
                    continue;
                }

                Optional<BlockTransactionHash> optionalReference = references.stream().filter(reference -> reference.getHash().equals(hash)).findFirst();
                if(optionalReference.isEmpty()) {
                    throw new IllegalStateException("Returned transaction " + hash.toString() + " that was not requested");
                }
                BlockTransactionHash reference = optionalReference.get();

                Date blockDate = null;
                if(reference.getHeight() > 0) {
                    BlockHeader blockHeader = blockHeaderMap.get(reference.getHeight());
                    if(blockHeader == null) {
                        transactionMap.put(hash, UNFETCHABLE_BLOCK_TRANSACTION);
                        checkReferences.removeIf(ref -> ref.getHash().equals(hash));
                        continue;
                    }
                    blockDate = blockHeader.getTimeAsDate();
                }

                BlockTransaction blockchainTransaction = new BlockTransaction(reference.getHash(), reference.getHeight(), blockDate, reference.getFee(), transaction);

                transactionMap.put(hash, blockchainTransaction);
                checkReferences.remove(reference);
            }

            if(!checkReferences.isEmpty()) {
                throw new IllegalStateException("Could not retrieve transactions " + checkReferences);
            }

            return transactionMap;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
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
        Script nodeScript = wallet.getOutputScript(node);
        Set<BlockTransactionHash> history = nodeTransactionMap.get(node);
        for(BlockTransactionHash reference : history) {
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

                Optional<BlockTransactionHash> optionalTxHash = history.stream().filter(txHash -> txHash.getHash().equals(previousHash)).findFirst();
                if(optionalTxHash.isEmpty()) {
                    //No previous transaction history found, cannot check if spends from wallet
                    //This is fine so long as all referenced transactions have been returned, in which case this refers to a transaction that does not affect this wallet node
                    continue;
                }

                BlockTransactionHash spentTxHash = optionalTxHash.get();
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

    public Map<Sha256Hash, BlockTransaction> getReferencedTransactions(Set<Sha256Hash> references, String scriptHash) throws ServerException {
        Set<String> txids = new LinkedHashSet<>(references.size());
        for(Sha256Hash reference : references) {
            txids.add(reference.toString());
        }

        Map<String, VerboseTransaction> result = electrumServerRpc.getVerboseTransactions(getTransport(), txids, scriptHash);

        Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
        for(String txid : result.keySet()) {
            Sha256Hash hash = Sha256Hash.wrap(txid);
            BlockTransaction blockTransaction = result.get(txid).getBlockTransaction();
            transactionMap.put(hash, blockTransaction);
        }

        return transactionMap;
    }

    public Map<Integer, Double> getFeeEstimates(List<Integer> targetBlocks) throws ServerException {
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

            FeeRatesSource feeRatesSource = Config.get().getFeeRatesSource();
            feeRatesSource = (feeRatesSource == null ? FeeRatesSource.MEMPOOL_SPACE : feeRatesSource);
            if(Network.get().equals(Network.MAINNET)) {
                targetBlocksFeeRatesSats.putAll(feeRatesSource.getBlockTargetFeeRates(targetBlocksFeeRatesSats));
            }

            return targetBlocksFeeRatesSats;
        } catch(ElectrumServerRpcException e) {
            throw new ServerException(e.getMessage(), e);
        }
    }

    public Set<MempoolRateSize> getMempoolRateSizes() throws ServerException {
        Map<Long, Long> feeRateHistogram = electrumServerRpc.getFeeRateHistogram(getTransport());
        Set<MempoolRateSize> mempoolRateSizes = new TreeSet<>();
        for(Long fee : feeRateHistogram.keySet()) {
            mempoolRateSizes.add(new MempoolRateSize(fee, feeRateHistogram.get(fee)));
        }

        return mempoolRateSizes;
    }

    public Double getMinimumRelayFee() throws ServerException {
        Double minFeeRateBtcKb = electrumServerRpc.getMinimumRelayFee(getTransport());
        if(minFeeRateBtcKb != null) {
            long minFeeRateSatsKb = (long)(minFeeRateBtcKb * Transaction.SATOSHIS_PER_BITCOIN);
            return minFeeRateSatsKb / 1000d;
        }

        return Transaction.DEFAULT_MIN_RELAY_FEE;
    }

    public Sha256Hash broadcastTransactionPrivately(Transaction transaction) throws ServerException {
        //If Tor proxy is configured, try all external broadcast sources in random order before falling back to connected Electrum server
        if(AppServices.isUsingProxy()) {
            List<BroadcastSource> broadcastSources = Arrays.stream(BroadcastSource.values()).filter(src -> src.getSupportedNetworks().contains(Network.get())).collect(Collectors.toList());
            Sha256Hash txid = null;
            for(int i = 1; !broadcastSources.isEmpty(); i++) {
                try {
                    BroadcastSource broadcastSource = broadcastSources.remove(new Random().nextInt(broadcastSources.size()));
                    txid = broadcastSource.broadcastTransaction(transaction);
                    if(Network.get() != Network.MAINNET || i >= MINIMUM_BROADCASTS || broadcastSources.isEmpty()) {
                        return txid;
                    }
                } catch(BroadcastSource.BroadcastException e) {
                    //ignore, already logged
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
            pathScriptHashes.put(node.getDerivationPath(), getScriptHash(wallet, node));
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

            try {
                Transaction transaction = new Transaction(Utils.hexToBytes(strRawTx));
                for(TransactionOutput txOutput : transaction.getOutputs()) {
                    if(txOutput.getScript().equals(outputScript)) {
                        transactionOutputs.add(txOutput);
                    }
                }
                transactions.add(transaction);
            } catch(ProtocolException e) {
                log.error("Could not parse tx: " + strRawTx);
            }
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
                scriptHashes.put(getScriptHash(wallet, childNode), childNode);
            }
        }

        return scriptHashes;
    }

    public static String getScriptHash(Wallet wallet, WalletNode node) {
        byte[] hash = Sha256Hash.hash(wallet.getOutputScript(node).getProgram());
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

    public static boolean supportsBatching(List<String> serverVersion) {
        if(serverVersion.size() > 0) {
            String server = serverVersion.get(0).toLowerCase();
            if(server.contains("electrumx")) {
                return true;
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
                        return true;
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
                        return true;
                    }
                } catch(Exception e) {
                    //ignore
                }
            }
        }

        return false;
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
                        Bwt.initialize();

                        if(!bwt.isRunning()) {
                            Bwt.ConnectionService bwtConnectionService = bwt.getConnectionService(subscribe ? AppServices.get().getOpenWallets().keySet() : null);
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
                                            throw new ServerException("Bitcoin Core requires Multi-Wallet to be enabled in the Server Preferences");
                                        } else if(walletLoadingMatcher.matches() && walletLoadingMatcher.group(1) != null) {
                                            throw new ServerException(walletLoadingMatcher.group(1));
                                        }
                                    }

                                    throw new ServerException("Check if Bitcoin Core is running, and the authentication details are correct.");
                                }
                            } catch(InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return null;
                            } finally {
                                bwtStartLock.unlock();
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

                        //If electrumx is detected, we can upgrade to batched RPC. Electrs/EPS do not support batching.
                        if(supportsBatching(serverVersion)) {
                            log.debug("Upgrading to batched JSON-RPC");
                            electrumServerRpc = new BatchedElectrumServerRpc(electrumServerRpc.getIdCounterValue());
                        }

                        BlockHeaderTip tip;
                        if(subscribe) {
                            tip = electrumServer.subscribeBlockHeaders();
                            subscribedScriptHashes.clear();
                        } else {
                            tip = new BlockHeaderTip();
                        }

                        String banner = electrumServer.getServerBanner();

                        Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(AppServices.TARGET_BLOCKS_RANGE);
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

                            long elapsed = System.currentTimeMillis() - feeRatesRetrievedAt;
                            if(elapsed > FEE_RATES_PERIOD) {
                                Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(AppServices.TARGET_BLOCKS_RANGE);
                                Set<MempoolRateSize> mempoolRateSizes = electrumServer.getMempoolRateSizes();
                                feeRatesRetrievedAt = System.currentTimeMillis();
                                return new FeeRatesUpdatedEvent(blockTargetFeeRates, mempoolRateSizes);
                            }
                        } else {
                            resetConnection();
                        }
                    }

                    return null;
                }
            };
        }

        public void resetConnection() {
            try {
                closeActiveConnection();
                shutdown();
                firstCall = true;
            } catch (ServerException e) {
                log.error("Error closing connection during connection reset", e);
            }
        }

        public boolean isConnecting() {
            return isRunning() && Config.get().getServerType() == ServerType.BITCOIN_CORE && bwt.isRunning() && !bwt.isReady();
        }

        public boolean isConnected() {
            return isRunning() && (Config.get().getServerType() != ServerType.BITCOIN_CORE || (bwt.isRunning() && bwt.isReady()));
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
            if(Config.get().getServerType() == ServerType.BITCOIN_CORE && bwt.isRunning()) {
                Bwt.DisconnectionService disconnectionService = bwt.getDisconnectionService();
                disconnectionService.setOnSucceeded(workerStateEvent -> {
                    ElectrumServer.bwtElectrumServer = null;
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
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error("Uncaught error in ConnectionService", e);
        }

        @Subscribe
        public void bwtElectrumReadyStatus(BwtElectrumReadyStatusEvent event) {
            if(this.isRunning()) {
                ElectrumServer.bwtElectrumServer = Protocol.TCP.toUrlString(HostAndPort.fromString(event.getElectrumAddr()));
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

    public static class TransactionHistoryService extends Service<Boolean> {
        private final Wallet wallet;
        private final Set<WalletNode> nodes;
        private final static Map<Wallet, Object> walletSynchronizeLocks = new HashMap<>();

        public TransactionHistoryService(Wallet wallet) {
            this.wallet = wallet;
            this.nodes = null;
        }

        public TransactionHistoryService(Wallet wallet, Set<WalletNode> nodes) {
            this.wallet = wallet;
            this.nodes = nodes;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ServerException {
                    boolean initial = (walletSynchronizeLocks.putIfAbsent(wallet, new Object()) == null);
                    synchronized(walletSynchronizeLocks.get(wallet)) {
                        if(initial) {
                            addCalculatedScriptHashes(wallet);
                        }

                        if(isConnected()) {
                            ElectrumServer electrumServer = new ElectrumServer();
                            Map<String, String> previousScriptHashes = getCalculatedScriptHashes(wallet);
                            Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = (nodes == null ? electrumServer.getHistory(wallet) : electrumServer.getHistory(wallet, nodes));
                            electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
                            electrumServer.calculateNodeHistory(wallet, nodeTransactionMap);

                            //Add all of the script hashes we have now fetched the history for so we don't need to fetch again until the script hash status changes
                            Set<WalletNode> updatedNodes = new HashSet<>();
                            Map<WalletNode, Set<BlockTransactionHashIndex>> walletNodes = wallet.getWalletNodes();
                            for(WalletNode node : (nodes == null ? walletNodes.keySet() : nodes)) {
                                String scriptHash = getScriptHash(wallet, node);
                                String subscribedStatus = getSubscribedScriptHashStatus(scriptHash);
                                if(!Objects.equals(subscribedStatus, retrievedScriptHashes.get(scriptHash))) {
                                    updatedNodes.add(node);
                                }
                                retrievedScriptHashes.put(scriptHash, subscribedStatus);
                            }

                            //If wallet was not empty, check if all used updated nodes have changed history
                            if(nodes == null && previousScriptHashes.values().stream().anyMatch(Objects::nonNull)) {
                                if(!updatedNodes.isEmpty() && updatedNodes.equals(walletNodes.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).map(Map.Entry::getKey).collect(Collectors.toSet()))) {
                                    //All used nodes on a non-empty wallet have changed history. Abort and trigger a full refresh.
                                    log.info("All used nodes on a non-empty wallet have changed history. Triggering a full wallet refresh.");
                                    throw new AllHistoryChangedException();
                                }
                            }

                            //Clear transaction outputs for nodes that have no history - this is useful when a transaction is replaced in the mempool
                            if(nodes != null) {
                                for(WalletNode node : nodes) {
                                    String scriptHash = getScriptHash(wallet, node);
                                    if(retrievedScriptHashes.get(scriptHash) == null && !node.getTransactionOutputs().isEmpty()) {
                                        log.debug("Clearing transaction history for " + node);
                                        node.getTransactionOutputs().clear();
                                    }
                                }
                            }

                            return true;
                        }

                        return false;
                    }
                }
            };
        }
    }

    public static class TransactionMempoolService extends ScheduledService<Set<String>> {
        private final Wallet wallet;
        private final Sha256Hash txId;
        private final Set<WalletNode> nodes;
        private final IntegerProperty iterationCount = new SimpleIntegerProperty(0);

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

        @Override
        protected Task<Set<String>> createTask() {
            return new Task<>() {
                protected Set<String> call() throws ServerException {
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
                        if(retrievedTransactions.get(ref) != null) {
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

        public TransactionOutputsReferenceService(Transaction transaction) {
            this.transaction = transaction;
            this.indexStart = 0;
            this.indexEnd = transaction.getOutputs().size();
        }

        public TransactionOutputsReferenceService(Transaction transaction, int indexStart, int indexEnd) {
            this.transaction = transaction;
            this.indexStart = Math.min(transaction.getOutputs().size(), indexStart);
            this.indexEnd = Math.min(transaction.getOutputs().size(), indexEnd);
        }

        @Override
        protected Task<List<BlockTransaction>> createTask() {
            return new Task<>() {
                protected List<BlockTransaction> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    List<Set<BlockTransactionHash>> outputTransactionReferences = electrumServer.getOutputTransactionReferences(transaction, indexStart, indexEnd);

                    Set<BlockTransactionHash> setReferences = new HashSet<>();
                    for(Set<BlockTransactionHash> outputReferences : outputTransactionReferences) {
                        if(outputReferences != null) {
                            setReferences.addAll(outputReferences);
                        }
                    }
                    setReferences.remove(null);
                    setReferences.remove(UNFETCHABLE_BLOCK_TRANSACTION);

                    List<BlockTransaction> blockTransactions = new ArrayList<>(transaction.getOutputs().size());
                    for(int i = 0; i < transaction.getOutputs().size(); i++) {
                        blockTransactions.add(null);
                    }

                    Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
                    if(!setReferences.isEmpty()) {
                        Map<Integer, BlockHeader> blockHeaderMap = electrumServer.getBlockHeaders(null, setReferences);
                        transactionMap = electrumServer.getTransactions(null, setReferences, blockHeaderMap);
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

    public static class BroadcastTransactionService extends Service<Sha256Hash> {
        private final Transaction transaction;

        public BroadcastTransactionService(Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        protected Task<Sha256Hash> createTask() {
            return new Task<>() {
                protected Sha256Hash call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.broadcastTransactionPrivately(transaction);
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
                    Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(AppServices.TARGET_BLOCKS_RANGE);
                    Set<MempoolRateSize> mempoolRateSizes = electrumServer.getMempoolRateSizes();
                    return new FeeRatesUpdatedEvent(blockTargetFeeRates, mempoolRateSizes);
                }
            };
        }
    }

    public static class WalletDiscoveryService extends Service<Optional<Wallet>> {
        private final List<Wallet> wallets;

        public WalletDiscoveryService(List<Wallet> wallets) {
            this.wallets = wallets;
        }

        @Override
        protected Task<Optional<Wallet>> createTask() {
            return new Task<>() {
                protected Optional<Wallet> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();

                    for(int i = 0; i < wallets.size(); i++) {
                        Wallet wallet = wallets.get(i);
                        updateProgress(i, wallets.size() + StandardAccount.values().length);
                        Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = new TreeMap<>();
                        electrumServer.getReferences(wallet, wallet.getNode(KeyPurpose.RECEIVE).getChildren(), nodeTransactionMap, 0);
                        if(nodeTransactionMap.values().stream().anyMatch(blockTransactionHashes -> !blockTransactionHashes.isEmpty())) {
                            Wallet masterWalletCopy = wallet.copy();
                            List<StandardAccount> searchAccounts = getStandardAccounts(wallet);
                            for(int j = 0; j < searchAccounts.size(); j++) {
                                StandardAccount standardAccount = searchAccounts.get(j);
                                Wallet childWallet = masterWalletCopy.addChildWallet(standardAccount);
                                Map<WalletNode, Set<BlockTransactionHash>> childTransactionMap = new TreeMap<>();
                                electrumServer.getReferences(childWallet, childWallet.getNode(KeyPurpose.RECEIVE).getChildren(), childTransactionMap, 0);
                                if(childTransactionMap.values().stream().anyMatch(blockTransactionHashes -> !blockTransactionHashes.isEmpty())) {
                                    wallet.addChildWallet(standardAccount);
                                }
                                updateProgress(i + j, wallets.size() + StandardAccount.values().length);
                            }

                            return Optional.of(wallet);
                        }
                    }

                    return Optional.empty();
                }
            };
        }

        private List<StandardAccount> getStandardAccounts(Wallet wallet) {
            List<StandardAccount> accounts = new ArrayList<>();
            for(StandardAccount account : StandardAccount.values()) {
                if(account != StandardAccount.ACCOUNT_0 && (!StandardAccount.WHIRLPOOL_ACCOUNTS.contains(account) || wallet.getScriptType() == ScriptType.P2WPKH)) {
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

        public AddressUtxosService(Address address) {
            this.address = address;
        }

        @Override
        protected Task<List<TransactionOutput>> createTask() {
            return new Task<>() {
                protected List<TransactionOutput> call() throws ServerException {
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
                    Wallet notificationWallet = wallet.getNotificationWallet();
                    WalletNode notificationNode = notificationWallet.getNode(KeyPurpose.NOTIFICATION);

                    for(Wallet childWallet : wallet.getChildWallets()) {
                        if(childWallet.isBip47()) {
                            WalletNode savedNotificationNode = childWallet.getNode(KeyPurpose.NOTIFICATION);
                            notificationNode.getTransactionOutputs().addAll(savedNotificationNode.getTransactionOutputs());
                            notificationWallet.updateTransactions(childWallet.getTransactions());
                        }
                    }

                    addCalculatedScriptHashes(notificationWallet, notificationNode);

                    ElectrumServer electrumServer = new ElectrumServer();
                    Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = electrumServer.getHistory(notificationWallet, List.of(notificationNode));
                    electrumServer.getReferencedTransactions(notificationWallet, nodeTransactionMap);
                    electrumServer.calculateNodeHistory(notificationWallet, nodeTransactionMap);

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
                                            Wallet addedWallet = wallet.addChildWallet(paymentCode, childScriptType, output, blkTx);
                                            if(payNym != null) {
                                                addedWallet.setLabel(payNym.nymName() + " " + childScriptType.getName());
                                            }
                                            //Check this is a valid payment code, will throw IllegalArgumentException if not
                                            addedWallet.getPubKey(new WalletNode(KeyPurpose.RECEIVE, 0));
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
            Soroban soroban = AppServices.getSorobanServices().getSoroban(walletId);
            try {
                return soroban.getPayNym(paymentCode.toString()).blockingFirst();
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
