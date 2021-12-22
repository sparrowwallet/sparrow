package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.JsonRpcClient;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.client.builder.BatchRequestBuilder;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcBatchException;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletHistoryStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.sparrowwallet.drongo.wallet.WalletNode.nodeRangesToString;

public class BatchedElectrumServerRpc implements ElectrumServerRpc {
    private static final Logger log = LoggerFactory.getLogger(BatchedElectrumServerRpc.class);
    static final int MAX_RETRIES = 5;
    static final int RETRY_DELAY = 1;

    private final AtomicLong idCounter = new AtomicLong();

    @Override
    public void ping(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            new RetryLogic<>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().method("server.ping").id(idCounter.incrementAndGet()).executeNullable());
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error pinging server", e);
        }
    }

    @Override
    public List<String> getServerVersion(Transport transport, String clientName, String[] supportedVersions) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return new RetryLogic<List<String>>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().returnAsList(String.class).method("server.version").id(idCounter.incrementAndGet()).param("client_name", clientName).param("protocol_version", supportedVersions).execute());
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error getting server version", e);
        }
    }

    @Override
    public String getServerBanner(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return new RetryLogic<String>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().returnAs(String.class).method("server.banner").id(idCounter.incrementAndGet()).execute());
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error getting server banner", e);
        }
    }

    @Override
    public BlockHeaderTip subscribeBlockHeaders(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return new RetryLogic<BlockHeaderTip>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().returnAs(BlockHeaderTip.class).method("blockchain.headers.subscribe").id(idCounter.incrementAndGet()).execute());
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error subscribing to block headers", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError) {
        PagedBatchRequestBuilder<String, ScriptHashTx[]> batchRequest = PagedBatchRequestBuilder.create(transport, idCounter).keysType(String.class).returnType(ScriptHashTx[].class);
        EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Loading transactions for " + nodeRangesToString(pathScriptHashes.keySet())));

        for(String path : pathScriptHashes.keySet()) {
            batchRequest.add(path, "blockchain.scripthash.get_history", pathScriptHashes.get(path));
        }

        try {
            return batchRequest.execute();
        } catch (JsonRpcBatchException e) {
            if(failOnError) {
                throw new ElectrumServerRpcException("Failed to retrieve transaction history for paths: " + nodeRangesToString((Collection<String>)e.getErrors().keySet()), e);
            }

            Map<String, ScriptHashTx[]> result = (Map<String, ScriptHashTx[]>)e.getSuccesses();
            for(Object key : e.getErrors().keySet()) {
                result.put((String)key, new ScriptHashTx[] {ScriptHashTx.ERROR_TX});
            }

            return result;
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Failed to retrieve transaction history for paths: " + nodeRangesToString(pathScriptHashes.keySet()), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, ScriptHashTx[]> getScriptHashMempool(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError) {
        PagedBatchRequestBuilder<String, ScriptHashTx[]> batchRequest = PagedBatchRequestBuilder.create(transport, idCounter).keysType(String.class).returnType(ScriptHashTx[].class);

        for(String path : pathScriptHashes.keySet()) {
            batchRequest.add(path, "blockchain.scripthash.get_mempool", pathScriptHashes.get(path));
        }

        try {
            return batchRequest.execute();
        } catch(JsonRpcBatchException e) {
            if(failOnError) {
                throw new ElectrumServerRpcException("Failed to retrieve mempool transactions for paths: " + nodeRangesToString((Collection<String>)e.getErrors().keySet()), e);
            }

            Map<String, ScriptHashTx[]> result = (Map<String, ScriptHashTx[]>)e.getSuccesses();
            for(Object key : e.getErrors().keySet()) {
                result.put((String)key, new ScriptHashTx[] {ScriptHashTx.ERROR_TX});
            }

            return result;
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Failed to retrieve mempool transactions for paths: " + nodeRangesToString(pathScriptHashes.keySet()), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes) {
        PagedBatchRequestBuilder<String, String> batchRequest = PagedBatchRequestBuilder.create(transport, idCounter).keysType(String.class).returnType(String.class);
        EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Finding transactions for " + nodeRangesToString(pathScriptHashes.keySet())));

        for(String path : pathScriptHashes.keySet()) {
            batchRequest.add(path, "blockchain.scripthash.subscribe", pathScriptHashes.get(path));
        }

        try {
            return batchRequest.execute();
        } catch(JsonRpcBatchException e) {
            //Even if we have some successes, failure to subscribe for all script hashes will result in outdated wallet view. Don't proceed.
            throw new ElectrumServerRpcException("Failed to subscribe to paths: " + nodeRangesToString((Collection<String>)e.getErrors().keySet()), e);
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Failed to subscribe to paths: " + nodeRangesToString(pathScriptHashes.keySet()), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Integer, String> getBlockHeaders(Transport transport, Wallet wallet, Set<Integer> blockHeights) {
        PagedBatchRequestBuilder<Integer, String> batchRequest = PagedBatchRequestBuilder.create(transport, idCounter).keysType(Integer.class).returnType(String.class);
        EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Retrieving " + blockHeights.size() + " block headers"));

        for(Integer height : blockHeights) {
            batchRequest.add(height, "blockchain.block.header", height);
        }

        try {
            return batchRequest.execute();
        } catch(JsonRpcBatchException e) {
            return (Map<Integer, String>)e.getSuccesses();
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Failed to retrieve block headers for block heights: " + blockHeights, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids) {
        PagedBatchRequestBuilder<String, String> batchRequest = PagedBatchRequestBuilder.create(transport, idCounter).keysType(String.class).returnType(String.class);
        EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Retrieving " + txids.size() + " transactions"));

        for(String txid : txids) {
            batchRequest.add(txid, "blockchain.transaction.get", txid);
        }

        try {
            return batchRequest.execute();
        } catch(JsonRpcBatchException e) {
            Map<String, String> result = (Map<String, String>)e.getSuccesses();

            String strErrorTx = Sha256Hash.ZERO_HASH.toString();
            for(Object hash : e.getErrors().keySet()) {
                String txhash = (String)hash;
                result.put(txhash, strErrorTx);
            }

            return result;
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Failed to retrieve transactions for txids: " + txids.stream().map(txid -> "[" + txid.substring(0, 6) + "]").collect(Collectors.toList()), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, VerboseTransaction> getVerboseTransactions(Transport transport, Set<String> txids, String scriptHash) {
        JsonRpcClient client = new JsonRpcClient(transport);
        BatchRequestBuilder<String, VerboseTransaction> batchRequest = client.createBatchRequest().keysType(String.class).returnType(VerboseTransaction.class);
        for(String txid : txids) {
            batchRequest.add(txid, "blockchain.transaction.get", txid, true);
        }

        try {
            //The server may return an error if the transaction has not yet been broadcasted - this is a valid state so only try once
            return new RetryLogic<Map<String, VerboseTransaction>>(1, RETRY_DELAY, IllegalStateException.class).getResult(batchRequest::execute);
        } catch(JsonRpcBatchException e) {
            log.debug("Some errors retrieving transactions: " + e.getErrors());
            return (Map<String, VerboseTransaction>)e.getSuccesses();
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Failed to retrieve verbose transactions for txids: " + txids, e);
        }
    }

    @Override
    public Map<Integer, Double> getFeeEstimates(Transport transport, List<Integer> targetBlocks) {
        JsonRpcClient client = new JsonRpcClient(transport);
        BatchRequestBuilder<Integer, Double> batchRequest = client.createBatchRequest().keysType(Integer.class).returnType(Double.class);
        for(Integer targetBlock : targetBlocks) {
            batchRequest.add(targetBlock, "blockchain.estimatefee", targetBlock);
        }

        try {
            return new RetryLogic<Map<Integer, Double>>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(batchRequest::execute);
        } catch(JsonRpcBatchException e) {
            throw new ElectrumServerRpcException("Error getting fee estimates", e);
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error getting fee estimates for target blocks: " + targetBlocks, e);
        }
    }

    @Override
    public Map<Long, Long> getFeeRateHistogram(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            BigInteger[][] feesArray = new RetryLogic<BigInteger[][]>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().returnAs(BigInteger[][].class).method("mempool.get_fee_histogram").id(idCounter.incrementAndGet()).execute());

            Map<Long, Long> feeRateHistogram = new TreeMap<>();
            for(BigInteger[] feePair : feesArray) {
                if(feePair[0].longValue() > 0) {
                    feeRateHistogram.put(feePair[0].longValue(), feePair[1].longValue());
                }
            }

            return feeRateHistogram;
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error getting fee rate histogram", e);
        }
    }

    @Override
    public Double getMinimumRelayFee(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return new RetryLogic<Double>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().returnAs(Double.class).method("blockchain.relayfee").id(idCounter.incrementAndGet()).execute());
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error getting minimum relay fee", e);
        }
    }

    @Override
    public String broadcastTransaction(Transport transport, String txHex) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return new RetryLogic<String>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().returnAs(String.class).method("blockchain.transaction.broadcast").id(idCounter.incrementAndGet()).params(txHex).execute());
        } catch(JsonRpcException e) {
            throw new ElectrumServerRpcException(e.getErrorMessage().getMessage(), e);
        } catch(Exception e) {
            throw new ElectrumServerRpcException("Error broadcasting transaction", e);
        }
    }
}
