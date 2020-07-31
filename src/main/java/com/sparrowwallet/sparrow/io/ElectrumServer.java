package com.sparrowwallet.sparrow.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.arteam.simplejsonrpc.client.*;
import com.github.arteam.simplejsonrpc.client.builder.BatchRequestBuilder;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcBatchException;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcMethod;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcParam;
import com.github.arteam.simplejsonrpc.core.annotation.JsonRpcService;
import com.github.arteam.simplejsonrpc.server.JsonRpcServer;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.ConnectionEvent;
import com.sparrowwallet.sparrow.event.FeeRatesUpdatedEvent;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.WalletNodeHistoryChangedEvent;
import com.sparrowwallet.sparrow.wallet.SendController;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.jetbrains.annotations.NotNull;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ElectrumServer {
    private static final String[] SUPPORTED_VERSIONS = new String[]{"1.3", "1.4.2"};

    public static final BlockTransaction UNFETCHABLE_BLOCK_TRANSACTION = new BlockTransaction(Sha256Hash.ZERO_HASH, 0, null, null, null);

    private static Transport transport;

    private static final Map<String, String> subscribedScriptHashes = Collections.synchronizedMap(new HashMap<>());

    private static synchronized Transport getTransport() throws ServerException {
        if(transport == null) {
            try {
                String electrumServer = Config.get().getElectrumServer();
                File electrumServerCert = Config.get().getElectrumServerCert();
                String proxyServer = Config.get().getProxyServer();

                if(electrumServer == null) {
                    throw new ServerException("Electrum server URL not specified");
                }

                if(electrumServerCert != null && !electrumServerCert.exists()) {
                    throw new ServerException("Electrum server certificate file not found");
                }

                Protocol protocol = Protocol.getProtocol(electrumServer);
                if(protocol == null) {
                    throw new ServerException("Electrum server URL must start with " + Protocol.TCP.toUrlString() + " or " + Protocol.SSL.toUrlString());
                }

                HostAndPort server = protocol.getServerHostAndPort(electrumServer);

                if(Config.get().isUseProxy() && proxyServer != null && !proxyServer.isBlank()) {
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
                throw new ServerException(e);
            }
        }

        return transport;
    }

    public void connect() throws ServerException {
        TcpTransport tcpTransport = (TcpTransport)getTransport();
        tcpTransport.connect();
    }

    public void ping() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        client.createRequest().method("server.ping").id(1).executeNullable();
    }

    public List<String> getServerVersion() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        return client.createRequest().returnAsList(String.class).method("server.version").id(1).param("client_name", "Sparrow").param("protocol_version", SUPPORTED_VERSIONS).execute();
    }

    public String getServerBanner() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        return client.createRequest().returnAs(String.class).method("server.banner").id(1).execute();
    }

    public BlockHeaderTip subscribeBlockHeaders() throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        return client.createRequest().returnAs(BlockHeaderTip.class).method("blockchain.headers.subscribe").id(1).execute();
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

    public Map<WalletNode, Set<BlockTransactionHash>> getHistory(Wallet wallet) throws ServerException {
        Map<WalletNode, Set<BlockTransactionHash>> receiveTransactionMap = new TreeMap<>();
        getHistory(wallet, KeyPurpose.RECEIVE, receiveTransactionMap);

        Map<WalletNode, Set<BlockTransactionHash>> changeTransactionMap = new TreeMap<>();
        getHistory(wallet, KeyPurpose.CHANGE, changeTransactionMap);

        receiveTransactionMap.putAll(changeTransactionMap);
        return receiveTransactionMap;
    }

    public void getHistory(Wallet wallet, KeyPurpose keyPurpose, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        WalletNode purposeNode = wallet.getNode(keyPurpose);
        getHistory(wallet, purposeNode.getChildren(), nodeTransactionMap, 0);

        //Because node children are added sequentially in WalletNode.fillToIndex, we can simply look at the number of children to determine the highest filled index
        int historySize = purposeNode.getChildren().size();
        //The gap limit size takes the highest used index in the retrieved history and adds the gap limit (plus one to be comparable to the number of children since index is zero based)
        int gapLimitSize = getGapLimitSize(nodeTransactionMap);
        while(historySize < gapLimitSize) {
            purposeNode.fillToIndex(gapLimitSize - 1);
            getHistory(wallet, purposeNode.getChildren(), nodeTransactionMap, historySize);
            historySize = purposeNode.getChildren().size();
            gapLimitSize = getGapLimitSize(nodeTransactionMap);
        }
    }

    private int getGapLimitSize(Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) {
        int highestIndex = nodeTransactionMap.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).map(entry -> entry.getKey().getIndex()).max(Comparator.comparing(Integer::valueOf)).orElse(-1);
        return highestIndex + Wallet.DEFAULT_LOOKAHEAD + 1;
    }

    public void getHistory(Wallet wallet, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, int startIndex) throws ServerException {
        getReferences(wallet, "blockchain.scripthash.get_history", nodes, nodeTransactionMap, startIndex);
        subscribeWalletNodes(wallet, nodes, startIndex);
    }

    public void getMempool(Wallet wallet, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, int startIndex) throws ServerException {
        getReferences(wallet, "blockchain.scripthash.get_mempool", nodes, nodeTransactionMap, startIndex);
    }

    public void getReferences(Wallet wallet, String method, Collection<WalletNode> nodes, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap, int startIndex) throws ServerException {
        try {
            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<String, ScriptHashTx[]> batchRequest = client.createBatchRequest().keysType(String.class).returnType(ScriptHashTx[].class);

            for(WalletNode node : nodes) {
                if(node.getIndex() >= startIndex) {
                    batchRequest.add(node.getDerivationPath(), method, getScriptHash(wallet, node));
                }
            }

            Map<String, ScriptHashTx[]> result;
            try {
                result = batchRequest.execute();
            } catch (JsonRpcBatchException e) {
                //Even if we have some successes, failure to retrieve all references will result in an incomplete wallet history. Don't proceed.
                throw new IllegalStateException("Failed to retrieve references for paths: " + e.getErrors().keySet());
            }

            for(String path : result.keySet()) {
                ScriptHashTx[] txes = result.get(path);

                Optional<WalletNode> optionalNode = nodes.stream().filter(n -> n.getDerivationPath().equals(path)).findFirst();
                if(optionalNode.isPresent()) {
                    WalletNode node = optionalNode.get();

                    Set<BlockTransactionHash> references = Arrays.stream(txes).map(ScriptHashTx::getBlockchainTransactionHash).collect(Collectors.toCollection(TreeSet::new));
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
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public void subscribeWalletNodes(Wallet wallet, Collection<WalletNode> nodes, int startIndex) throws ServerException {
        try {
            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<String, String> batchRequest = client.createBatchRequest().keysType(String.class).returnType(String.class);

            Set<String> scriptHashes = new HashSet<>();
            for(WalletNode node : nodes) {
                if(node.getIndex() >= startIndex) {
                    String scriptHash = getScriptHash(wallet, node);
                    if(!subscribedScriptHashes.containsKey(scriptHash) && scriptHashes.add(scriptHash)) {
                        batchRequest.add(node.getDerivationPath(), "blockchain.scripthash.subscribe", scriptHash);
                    }
                }
            }

            if(scriptHashes.isEmpty()) {
                return;
            }

            Map<String, String> result;
            try {
                result = batchRequest.execute();
            } catch(JsonRpcBatchException e) {
                //Even if we have some successes, failure to subscribe for all script hashes will result in outdated wallet view. Don't proceed.
                throw new IllegalStateException("Failed to subscribe for updates for paths: " + e.getErrors().keySet());
            }

            for(String path : result.keySet()) {
                String status = result.get(path);

                Optional<WalletNode> optionalNode = nodes.stream().filter(n -> n.getDerivationPath().equals(path)).findFirst();
                if(optionalNode.isPresent()) {
                    WalletNode node = optionalNode.get();
                    subscribedScriptHashes.put(getScriptHash(wallet, node), status);
                }
            }
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Set<BlockTransactionHash>> getOutputTransactionReferences(Transaction transaction, int indexStart, int indexEnd) throws ServerException {
        try {
            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<Integer, ScriptHashTx[]> batchRequest = client.createBatchRequest().keysType(Integer.class).returnType(ScriptHashTx[].class);
            for(int i = indexStart; i < transaction.getOutputs().size() && i < indexEnd; i++) {
                TransactionOutput output = transaction.getOutputs().get(i);
                batchRequest.add(i, "blockchain.scripthash.get_history", getScriptHash(output));
            }

            Map<Integer, ScriptHashTx[]> result;
            try {
                result = batchRequest.execute();
            } catch (JsonRpcBatchException e) {
                result = (Map<Integer, ScriptHashTx[]>)e.getSuccesses();
                for(Object index : e.getErrors().keySet()) {
                    Integer i = (Integer)index;
                    result.put(i, new ScriptHashTx[] {ScriptHashTx.ERROR_TX});
                }
            }

            List<Set<BlockTransactionHash>> blockTransactionHashes = new ArrayList<>(transaction.getOutputs().size());
            for(int i = 0; i < transaction.getOutputs().size(); i++) {
                blockTransactionHashes.add(null);
            }

            for(Integer index : result.keySet()) {
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

                blockTransactionHashes.set(index, references);
            }

            return blockTransactionHashes;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    public void getReferencedTransactions(Wallet wallet, Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap) throws ServerException {
        Set<BlockTransactionHash> references = new TreeSet<>();
        for(Set<BlockTransactionHash> nodeReferences : nodeTransactionMap.values()) {
            references.addAll(nodeReferences);
        }

        Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
        if(!references.isEmpty()) {
            Map<Integer, BlockHeader> blockHeaderMap = getBlockHeaders(references);
            transactionMap = getTransactions(references, blockHeaderMap);
        }

        if(!transactionMap.equals(wallet.getTransactions())) {
            wallet.updateTransactions(transactionMap);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, BlockHeader> getBlockHeaders(Set<BlockTransactionHash> references) throws ServerException {
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

            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<Integer, String> batchRequest = client.createBatchRequest().keysType(Integer.class).returnType(String.class);
            for(Integer height : blockHeights) {
                batchRequest.add(height, "blockchain.block.header", height);
            }

            Map<Integer, String> result;
            try {
                result = batchRequest.execute();
            } catch (JsonRpcBatchException e) {
                result = (Map<Integer, String>)e.getSuccesses();
            }

            Map<Integer, BlockHeader> blockHeaderMap = new TreeMap<>();
            for(Integer height : result.keySet()) {
                byte[] blockHeaderBytes = Utils.hexToBytes(result.get(height));
                BlockHeader blockHeader = new BlockHeader(blockHeaderBytes);
                blockHeaderMap.put(height, blockHeader);
                blockHeights.remove(height);
            }

            if(!blockHeights.isEmpty()) {
                System.out.println("Could not retrieve " + blockHeights.size() + " blocks");
            }

            return blockHeaderMap;
        } catch (IllegalStateException e) {
            throw new ServerException(e.getCause());
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<Sha256Hash, BlockTransaction> getTransactions(Set<BlockTransactionHash> references, Map<Integer, BlockHeader> blockHeaderMap) throws ServerException {
        try {
            Set<BlockTransactionHash> checkReferences = new TreeSet<>(references);

            JsonRpcClient client = new JsonRpcClient(getTransport());
            BatchRequestBuilder<String, String> batchRequest = client.createBatchRequest().keysType(String.class).returnType(String.class);
            for(BlockTransactionHash reference : references) {
                batchRequest.add(reference.getHashAsString(), "blockchain.transaction.get", reference.getHashAsString());
            }

            String strErrorTx = Sha256Hash.ZERO_HASH.toString();
            Map<String, String> result;
            try {
                result = batchRequest.execute();
            } catch (JsonRpcBatchException e) {
                result = (Map<String, String>)e.getSuccesses();
                for(Object hash : e.getErrors().keySet()) {
                    String txhash = (String)hash;
                    result.put(txhash, strErrorTx);
                }
            }

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
                Transaction transaction = new Transaction(rawtx);

                Optional<BlockTransactionHash> optionalReference = references.stream().filter(reference -> reference.getHash().equals(hash)).findFirst();
                if(optionalReference.isEmpty()) {
                    throw new IllegalStateException("Returned transaction " + hash.toString() + " that was not requested");
                }
                BlockTransactionHash reference = optionalReference.get();

                Date blockDate;
                if(reference.getHeight() > 0) {
                    BlockHeader blockHeader = blockHeaderMap.get(reference.getHeight());
                    if(blockHeader == null) {
                        transactionMap.put(hash, UNFETCHABLE_BLOCK_TRANSACTION);
                        checkReferences.removeIf(ref -> ref.getHash().equals(hash));
                        continue;
                    }
                    blockDate = blockHeader.getTimeAsDate();
                } else {
                    blockDate = new Date();
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
            if(blockTransaction == null || blockTransaction.equals(UNFETCHABLE_BLOCK_TRANSACTION)) {
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
            node.updateTransactionOutputs(transactionOutputs);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<Sha256Hash, BlockTransaction> getReferencedTransactions(Set<Sha256Hash> references) throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        BatchRequestBuilder<String, VerboseTransaction> batchRequest = client.createBatchRequest().keysType(String.class).returnType(VerboseTransaction.class);
        for(Sha256Hash reference : references) {
            batchRequest.add(reference.toString(), "blockchain.transaction.get", reference.toString(), true);
        }

        Map<String, VerboseTransaction> result;
        try {
            result = batchRequest.execute();
        } catch (JsonRpcBatchException e) {
            System.out.println("Some errors retrieving transactions: " + e.getErrors());
            result = (Map<String, VerboseTransaction>)e.getSuccesses();
        }

        Map<Sha256Hash, BlockTransaction> transactionMap = new HashMap<>();
        for(String txid : result.keySet()) {
            Sha256Hash hash = Sha256Hash.wrap(txid);
            BlockTransaction blockTransaction = result.get(txid).getBlockTransaction();
            transactionMap.put(hash, blockTransaction);
        }

        return transactionMap;
    }

    public Map<Integer, Double> getFeeEstimates(List<Integer> targetBlocks) throws ServerException {
        JsonRpcClient client = new JsonRpcClient(getTransport());
        BatchRequestBuilder<Integer, Double> batchRequest = client.createBatchRequest().keysType(Integer.class).returnType(Double.class);
        for(Integer targetBlock : targetBlocks) {
            batchRequest.add(targetBlock, "blockchain.estimatefee", targetBlock);
        }

        Map<Integer, Double> targetBlocksFeeRatesBtcKb = batchRequest.execute();

        Map<Integer, Double> targetBlocksFeeRatesSats = new TreeMap<>();
        for(Integer target : targetBlocksFeeRatesBtcKb.keySet()) {
            targetBlocksFeeRatesSats.put(target, targetBlocksFeeRatesBtcKb.get(target) * Transaction.SATOSHIS_PER_BITCOIN / 1024);
        }

        return targetBlocksFeeRatesSats;
    }

    public Sha256Hash broadcastTransaction(Transaction transaction) throws ServerException {
        byte[] rawtxBytes = transaction.bitcoinSerialize();
        String rawtxHex = Utils.bytesToHex(rawtxBytes);

        JsonRpcClient client = new JsonRpcClient(getTransport());
        try {
            String strTxHash = client.createRequest().returnAs(String.class).method("blockchain.transaction.broadcast").id(1).param("raw_tx", rawtxHex).execute();
            Sha256Hash receivedTxid = Sha256Hash.wrap(strTxHash);
            if(!receivedTxid.equals(transaction.getTxId())) {
                throw new ServerException("Received txid was different (" + receivedTxid + ")");
            }

            return receivedTxid;
        } catch(JsonRpcException e) {
            throw new ServerException(e.getErrorMessage().getMessage());
        } catch(IllegalStateException e) {
            throw new ServerException(e.getMessage());
        }
    }

    public static String getScriptHash(Wallet wallet, WalletNode node) {
        byte[] hash = Sha256Hash.hash(wallet.getOutputScript(node).getProgram());
        byte[] reversed = Utils.reverseBytes(hash);
        return Utils.bytesToHex(reversed);
    }

    private String getScriptHash(TransactionOutput output) {
        byte[] hash = Sha256Hash.hash(output.getScript().getProgram());
        byte[] reversed = Utils.reverseBytes(hash);
        return Utils.bytesToHex(reversed);
    }

    private static class ScriptHashTx {
        public static final ScriptHashTx ERROR_TX = new ScriptHashTx() {
            @Override
            public BlockTransactionHash getBlockchainTransactionHash() {
                return UNFETCHABLE_BLOCK_TRANSACTION;
            }
        };

        public int height;
        public String tx_hash;
        public long fee;

        public BlockTransactionHash getBlockchainTransactionHash() {
            Sha256Hash hash = Sha256Hash.wrap(tx_hash);
            return new BlockTransaction(hash, height, null, fee, null);
        }

        @Override
        public String toString() {
            return "ScriptHashTx{height=" + height + ", tx_hash='" + tx_hash + '\'' + ", fee=" + fee + '}';
        }
    }

    private static class BlockHeaderTip {
        public int height;
        public String hex;

        public BlockHeader getBlockHeader() {
            if(hex == null) {
                return null;
            }

            byte[] blockHeaderBytes = Utils.hexToBytes(hex);
            return new BlockHeader(blockHeaderBytes);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown=true)
    private static class VerboseTransaction {
        public String blockhash;
        public long blocktime;
        public int confirmations;
        public String hash;
        public String hex;
        public int locktime;
        public long size;
        public String txid;
        public int version;

        public int getHeight() {
            Integer currentHeight = AppController.getCurrentBlockHeight();
            if(currentHeight != null) {
                return currentHeight - confirmations + 1;
            }

            return -1;
        }

        public Date getDate() {
            return new Date(blocktime * 1000);
        }

        public BlockTransaction getBlockTransaction() {
            return new BlockTransaction(Sha256Hash.wrap(txid), getHeight(), getDate(), 0L, new Transaction(Utils.hexToBytes(hex)), blockhash == null ? null : Sha256Hash.wrap(blockhash));
        }
    }

    @JsonRpcService
    public static class SubscriptionService {
        @JsonRpcMethod("blockchain.headers.subscribe")
        public void newBlockHeaderTip(@JsonRpcParam("header") final BlockHeaderTip header) {
            Platform.runLater(() -> EventManager.get().post(new NewBlockEvent(header.height, header.getBlockHeader())));
        }

        @JsonRpcMethod("blockchain.scripthash.subscribe")
        public void scriptHashStatusUpdated(@JsonRpcParam("scripthash") final String scriptHash, @JsonRpcParam("status") final String status) {
            String oldStatus = subscribedScriptHashes.put(scriptHash, status);
            if(Objects.equals(oldStatus, status)) {
                System.out.println("Received script hash status update, but status has not changed");
            }

            Platform.runLater(() -> EventManager.get().post(new WalletNodeHistoryChangedEvent(scriptHash)));
        }
    }

    public static class TcpTransport implements Transport, Closeable {
        public static final int DEFAULT_PORT = 50001;

        protected final HostAndPort server;
        protected final SocketFactory socketFactory;

        private Socket socket;

        private String response;

        private final ReentrantLock clientRequestLock = new ReentrantLock();
        private boolean running = false;
        private boolean reading = true;

        private final JsonRpcServer jsonRpcServer = new JsonRpcServer();
        private final SubscriptionService subscriptionService = new SubscriptionService();

        public TcpTransport(HostAndPort server) {
            this.server = server;
            this.socketFactory = SocketFactory.getDefault();
        }

        @Override
        public @NotNull String pass(@NotNull String request) throws IOException {
            clientRequestLock.lock();
            try {
                writeRequest(request);
                return readResponse();
            } finally {
                clientRequestLock.unlock();
            }
        }

        private void writeRequest(String request) throws IOException {
            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
            out.println(request);
            out.flush();
        }

        private synchronized String readResponse() {
            while(reading) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    //Restore interrupt status and continue
                    Thread.currentThread().interrupt();
                }
            }

            reading = true;

            notifyAll();
            return response;
        }

        public synchronized void readInputLoop() throws ServerException {
            while(running) {
                try {
                    String received = readInputStream();
                    if(received.contains("method")) {
                        //Handle subscription notification
                        jsonRpcServer.handle(received, subscriptionService);
                    } else {
                        //Handle client's response
                        response = received;
                        reading = false;
                        notifyAll();
                        wait();
                    }
                } catch(InterruptedException e) {
                    //Restore interrupt status and continue
                    Thread.currentThread().interrupt();
                } catch(IOException e) {
                    if(running) {
                        throw new ServerException(e);
                    }
                }
            }
        }

        protected String readInputStream() throws IOException {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = in.readLine();

            if(response == null) {
                throw new IOException("Could not connect to server at " + Config.get().getElectrumServer());
            }

            return response;
        }

        public void connect() throws ServerException {
            try {
                socket = createSocket();
                running = true;
            } catch (IOException e) {
                throw new ServerException(e);
            }
        }

        public boolean isConnected() {
            return socket != null && running;
        }

        protected Socket createSocket() throws IOException {
            return socketFactory.createSocket(server.getHost(), server.getPortOrDefault(DEFAULT_PORT));
        }

        @Override
        public void close() throws IOException {
            if(socket != null) {
                running = false;
                socket.close();
            }
        }
    }

    public static class TcpOverTlsTransport extends TcpTransport {
        public static final int DEFAULT_PORT = 50002;

        protected final SSLSocketFactory sslSocketFactory;

        public TcpOverTlsTransport(HostAndPort server) throws NoSuchAlgorithmException, KeyManagementException {
            super(server);

            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            this.sslSocketFactory = sslContext.getSocketFactory();
        }

        public TcpOverTlsTransport(HostAndPort server, File crtFile) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            super(server);

            Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("electrumx", certificate);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            sslSocketFactory = sslContext.getSocketFactory();
        }

        protected Socket createSocket() throws IOException {
            SSLSocket sslSocket = (SSLSocket)sslSocketFactory.createSocket(server.getHost(), server.getPortOrDefault(DEFAULT_PORT));
            sslSocket.startHandshake();

            return sslSocket;
        }
    }

    public static class ProxyTcpOverTlsTransport extends TcpOverTlsTransport {
        public static final int DEFAULT_PROXY_PORT = 1080;

        private final HostAndPort proxy;

        public ProxyTcpOverTlsTransport(HostAndPort server, HostAndPort proxy) throws KeyManagementException, NoSuchAlgorithmException {
            super(server);
            this.proxy = proxy;
        }

        public ProxyTcpOverTlsTransport(HostAndPort server, File crtFile, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            super(server, crtFile);
            this.proxy = proxy;
        }

        @Override
        protected Socket createSocket() throws IOException {
            InetSocketAddress proxyAddr = new InetSocketAddress(proxy.getHost(), proxy.getPortOrDefault(DEFAULT_PROXY_PORT));
            Socket underlying = new Socket(new Proxy(Proxy.Type.SOCKS, proxyAddr));
            underlying.connect(new InetSocketAddress(server.getHost(), server.getPortOrDefault(DEFAULT_PORT)));
            SSLSocket sslSocket = (SSLSocket)sslSocketFactory.createSocket(underlying, proxy.getHost(), proxy.getPortOrDefault(DEFAULT_PROXY_PORT), true);
            sslSocket.startHandshake();

            return sslSocket;
        }
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
        private static final int FEE_RATES_PERIOD = 5 * 60 * 1000;

        private final boolean subscribe;
        private boolean firstCall = true;
        private Thread reader;
        private Throwable lastReaderException;
        private long feeRatesRetrievedAt;

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
                    if(firstCall) {
                        electrumServer.connect();

                        reader = new Thread(new ReadRunnable());
                        reader.setDaemon(true);
                        reader.setUncaughtExceptionHandler(ConnectionService.this);
                        reader.start();

                        List<String> serverVersion = electrumServer.getServerVersion();
                        firstCall = false;

                        BlockHeaderTip tip;
                        if(subscribe) {
                            tip = electrumServer.subscribeBlockHeaders();
                            subscribedScriptHashes.clear();
                        } else {
                            tip = new BlockHeaderTip();
                        }

                        String banner = electrumServer.getServerBanner();

                        Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(SendController.TARGET_BLOCKS_RANGE);
                        feeRatesRetrievedAt = System.currentTimeMillis();

                        return new ConnectionEvent(serverVersion, banner, tip.height, tip.getBlockHeader(), blockTargetFeeRates);
                    } else {
                        if(reader.isAlive()) {
                            electrumServer.ping();

                            long elapsed = System.currentTimeMillis() - feeRatesRetrievedAt;
                            if(elapsed > FEE_RATES_PERIOD) {
                                Map<Integer, Double> blockTargetFeeRates = electrumServer.getFeeEstimates(SendController.TARGET_BLOCKS_RANGE);
                                feeRatesRetrievedAt = System.currentTimeMillis();
                                return new FeeRatesUpdatedEvent(blockTargetFeeRates);
                            }
                        } else {
                            firstCall = true;
                            throw new ServerException("Connection to server failed", lastReaderException);
                        }
                    }

                    return null;
                }
            };
        }

        @Override
        public boolean cancel() {
            try {
                closeActiveConnection();
            } catch (ServerException e) {
                e.printStackTrace();
            }

            return super.cancel();
        }

        @Override
        public void reset() {
            super.reset();
            firstCall = true;
            lastReaderException = null;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            this.lastReaderException = e;
        }
    }

    public static class ReadRunnable implements Runnable {
        @Override
        public void run() {
            try {
                TcpTransport tcpTransport = (TcpTransport)getTransport();
                tcpTransport.readInputLoop();
            } catch (ServerException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            }
        }
    }

    public static class TransactionHistoryService extends Service<Boolean> {
        private final Wallet wallet;

        public TransactionHistoryService(Wallet wallet) {
            this.wallet = wallet;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    Map<WalletNode, Set<BlockTransactionHash>> nodeTransactionMap = electrumServer.getHistory(wallet);
                    electrumServer.getReferencedTransactions(wallet, nodeTransactionMap);
                    electrumServer.calculateNodeHistory(wallet, nodeTransactionMap);
                    return true;
                }
            };
        }
    }

    public static class TransactionReferenceService extends Service<Map<Sha256Hash, BlockTransaction>> {
        private final Set<Sha256Hash> references;

        public TransactionReferenceService(Transaction transaction) {
            references = new HashSet<>();
            references.add(transaction.getTxId());
            for(TransactionInput input : transaction.getInputs()) {
                references.add(input.getOutpoint().getHash());
            }
        }

        public TransactionReferenceService(Set<Sha256Hash> references) {
            this.references = references;
        }

        @Override
        protected Task<Map<Sha256Hash, BlockTransaction>> createTask() {
            return new Task<>() {
                protected Map<Sha256Hash, BlockTransaction> call() throws ServerException {
                    ElectrumServer electrumServer = new ElectrumServer();
                    return electrumServer.getReferencedTransactions(references);
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
                        Map<Integer, BlockHeader> blockHeaderMap = electrumServer.getBlockHeaders(setReferences);
                        transactionMap = electrumServer.getTransactions(setReferences, blockHeaderMap);
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
                    return electrumServer.broadcastTransaction(transaction);
                }
            };
        }
    }

    public enum Protocol {
        TCP {
            @Override
            public Transport getTransport(HostAndPort server) {
                return new TcpTransport(server);
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert) {
                return new TcpTransport(server);
            }

            @Override
            public Transport getTransport(HostAndPort server, HostAndPort proxy) {
                throw new UnsupportedOperationException("TCP protocol does not support proxying");
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) {
                throw new UnsupportedOperationException("TCP protocol does not support proxying");
            }
        },
        SSL{
            @Override
            public Transport getTransport(HostAndPort server) throws KeyManagementException, NoSuchAlgorithmException {
                return new TcpOverTlsTransport(server);
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
                return new TcpOverTlsTransport(server, serverCert);
            }

            @Override
            public Transport getTransport(HostAndPort server, HostAndPort proxy) throws NoSuchAlgorithmException, KeyManagementException {
                return new ProxyTcpOverTlsTransport(server, proxy);
            }

            @Override
            public Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
                return new ProxyTcpOverTlsTransport(server, serverCert, proxy);
            }
        };

        public abstract Transport getTransport(HostAndPort server) throws KeyManagementException, NoSuchAlgorithmException;

        public abstract Transport getTransport(HostAndPort server, File serverCert) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

        public abstract Transport getTransport(HostAndPort server, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

        public abstract Transport getTransport(HostAndPort server, File serverCert, HostAndPort proxy) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException;

        public HostAndPort getServerHostAndPort(String url) {
            return HostAndPort.fromString(url.substring(this.toUrlString().length()));
        }

        public String toUrlString() {
            return toString().toLowerCase() + "://";
        }

        public String toUrlString(String host) {
            return toUrlString(HostAndPort.fromHost(host));
        }

        public String toUrlString(String host, int port) {
            return toUrlString(HostAndPort.fromParts(host, port));
        }

        public String toUrlString(HostAndPort hostAndPort) {
            return toUrlString() + hostAndPort.toString();
        }

        public static Protocol getProtocol(String url) {
            if(url.startsWith("tcp://")) {
                return TCP;
            }
            if(url.startsWith("ssl://")) {
                return SSL;
            }

            return null;
        }
    }
}
