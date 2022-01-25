package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.JsonRpcClient;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletHistoryStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleElectrumServerRpc implements ElectrumServerRpc {
    private static final Logger log = LoggerFactory.getLogger(SimpleElectrumServerRpc.class);
    private static final int MAX_TARGET_BLOCKS = 25;
    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY = 1;

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
            //Using 1.4 as the version number as EPS tries to parse this number to a float :(
            return new RetryLogic<List<String>>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                    client.createRequest().returnAsList(String.class).method("server.version").id(idCounter.incrementAndGet()).params(clientName, "1.4").execute());
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
    public Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, ScriptHashTx[]> result = new LinkedHashMap<>();
        for(String path : pathScriptHashes.keySet()) {
            EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Loading transactions for " + path));
            try {
                ScriptHashTx[] scriptHashTxes = new RetryLogic<ScriptHashTx[]>(MAX_RETRIES, RETRY_DELAY, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(() ->
                        client.createRequest().returnAs(ScriptHashTx[].class).method("blockchain.scripthash.get_history").id(path + "-" + idCounter.incrementAndGet()).params(pathScriptHashes.get(path)).execute());
                result.put(path, scriptHashTxes);
            } catch(Exception e) {
                if(failOnError) {
                    throw new ElectrumServerRpcException("Failed to retrieve transaction history for path: " + path, e);
                }

                result.put(path, new ScriptHashTx[] {ScriptHashTx.ERROR_TX});
            }
        }

        return result;
    }

    @Override
    public Map<String, ScriptHashTx[]> getScriptHashMempool(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, ScriptHashTx[]> result = new LinkedHashMap<>();
        for(String path : pathScriptHashes.keySet()) {
            try {
                ScriptHashTx[] scriptHashTxes = new RetryLogic<ScriptHashTx[]>(MAX_RETRIES, RETRY_DELAY, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(() ->
                        client.createRequest().returnAs(ScriptHashTx[].class).method("blockchain.scripthash.get_mempool").id(path + "-" + idCounter.incrementAndGet()).params(pathScriptHashes.get(path)).execute());
                result.put(path, scriptHashTxes);
            } catch(Exception e) {
                if(failOnError) {
                    throw new ElectrumServerRpcException("Failed to retrieve mempool transactions for path: " + path, e);
                }

                result.put(path, new ScriptHashTx[] {ScriptHashTx.ERROR_TX});
            }
        }

        return result;
    }

    @Override
    public Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, String> result = new LinkedHashMap<>();
        for(String path : pathScriptHashes.keySet()) {
            EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Finding transactions for " + path));
            try {
                String scriptHash = new RetryLogic<String>(MAX_RETRIES, RETRY_DELAY, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(() ->
                        client.createRequest().returnAs(String.class).method("blockchain.scripthash.subscribe").id(path + "-" + idCounter.incrementAndGet()).params(pathScriptHashes.get(path)).executeNullable());
                result.put(path, scriptHash);
            } catch(Exception e) {
                //Even if we have some successes, failure to subscribe for all script hashes will result in outdated wallet view. Don't proceed.
                throw new ElectrumServerRpcException("Failed to subscribe to path: " + path, e);
            }
        }

        return result;
    }

    @Override
    public Map<Integer, String> getBlockHeaders(Transport transport, Wallet wallet, Set<Integer> blockHeights) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<Integer, String> result = new LinkedHashMap<>();
        for(Integer blockHeight : blockHeights) {
            EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Retrieving block at height " + blockHeight));
            try {
                String blockHeader = new RetryLogic<String>(MAX_RETRIES, RETRY_DELAY, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(() ->
                        client.createRequest().returnAs(String.class).method("blockchain.block.header").id(idCounter.incrementAndGet()).params(blockHeight).execute());
                result.put(blockHeight, blockHeader);
            } catch(ServerException e) {
                //If there is an error with the server connection, don't keep trying - this may take too long given many blocks
                throw new ElectrumServerRpcException("Failed to retrieve block header for block height: " + blockHeight, e);
            } catch(JsonRpcException e) {
                log.warn("Failed to retrieve block header for block height: " + blockHeight + " (" + e.getErrorMessage() + ")");
            } catch(Exception e) {
                log.warn("Failed to retrieve block header for block height: " + blockHeight + " (" + e.getMessage() + ")");
            }
        }

        return result;
    }

    @Override
    public Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, String> result = new LinkedHashMap<>();
        for(String txid : txids) {
            EventManager.get().post(new WalletHistoryStatusEvent(wallet, true, "Retrieving transaction [" + txid.substring(0, 6) + "]"));
            try {
                String rawTxHex = new RetryLogic<String>(MAX_RETRIES, RETRY_DELAY, List.of(IllegalStateException.class, IllegalArgumentException.class)).getResult(() ->
                        client.createRequest().returnAs(String.class).method("blockchain.transaction.get").id(idCounter.incrementAndGet()).params(txid).execute());
                result.put(txid, rawTxHex);
            } catch(ServerException e) {
                //If there is an error with the server connection, don't keep trying - this may take too long given many txids
                throw new ElectrumServerRpcException("Failed to retrieve transaction for txid [" + txid.substring(0, 6) + "]", e);
            } catch(Exception e) {
                result.put(txid, Sha256Hash.ZERO_HASH.toString());
            }
        }

        return result;
    }

    @Override
    public Map<String, VerboseTransaction> getVerboseTransactions(Transport transport, Set<String> txids, String scriptHash) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, VerboseTransaction> result = new LinkedHashMap<>();
        for(String txid : txids) {
            try {
                //The server may return an error if the transaction has not yet been broadcasted - this is a valid state so only try once
                VerboseTransaction verboseTransaction = new RetryLogic<VerboseTransaction>(1, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                        client.createRequest().returnAs(VerboseTransaction.class).method("blockchain.transaction.get").id(idCounter.incrementAndGet()).params(txid, true).execute());
                result.put(txid, verboseTransaction);
            } catch(Exception e) {
                //electrs-esplora does not currently support the verbose parameter, so try to fetch an incomplete VerboseTransaction without it
                //Note that without the script hash associated with the transaction, we can't get a block height as there is no way in the Electrum RPC protocol to do this
                //We mark this VerboseTransaction as incomplete by assigning it a Sha256Hash.ZERO_HASH blockhash
                log.debug("Error retrieving transaction: " + txid + " (" + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()) + ")");

                try {
                    String rawTxHex = client.createRequest().returnAs(String.class).method("blockchain.transaction.get").id(idCounter.incrementAndGet()).params(txid).execute();
                    Transaction tx = new Transaction(Utils.hexToBytes(rawTxHex));
                    String id = tx.getTxId().toString();
                    int height = 0;

                    if(scriptHash != null) {
                        ScriptHashTx[] scriptHashTxes = client.createRequest().returnAs(ScriptHashTx[].class).method("blockchain.scripthash.get_history").id(idCounter.incrementAndGet()).params(scriptHash).execute();
                        for(ScriptHashTx scriptHashTx : scriptHashTxes) {
                            if(scriptHashTx.tx_hash.equals(id)) {
                                height = scriptHashTx.height;
                                break;
                            }
                        }
                    }

                    VerboseTransaction verboseTransaction = new VerboseTransaction();
                    verboseTransaction.txid = id;
                    verboseTransaction.hex = rawTxHex;
                    verboseTransaction.confirmations = (height <= 0 ? 0 : AppServices.getCurrentBlockHeight() - height + 1);
                    verboseTransaction.blockhash = Sha256Hash.ZERO_HASH.toString();
                    result.put(txid, verboseTransaction);
                } catch(Exception ex) {
                    //ignore
                }
            }
        }

        return result;
    }

    @Override
    public Map<Integer, Double> getFeeEstimates(Transport transport, List<Integer> targetBlocks) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<Integer, Double> result = new LinkedHashMap<>();
        for(Integer targetBlock : targetBlocks) {
            if(targetBlock <= MAX_TARGET_BLOCKS) {
                try {
                    Double targetBlocksFeeRateBtcKb = new RetryLogic<Double>(MAX_RETRIES, RETRY_DELAY, IllegalStateException.class).getResult(() ->
                            client.createRequest().returnAs(Double.class).method("blockchain.estimatefee").id(idCounter.incrementAndGet()).params(targetBlock).execute());
                    result.put(targetBlock, targetBlocksFeeRateBtcKb);
                } catch(JsonRpcException e) {
                    throw new ElectrumServerRpcException("Failed to retrieve fee rate for target blocks: " + targetBlock, e);
                } catch(Exception e) {
                    log.warn("Failed to retrieve fee rate for target blocks: " + targetBlock + " (" + e.getMessage() + ")");
                    result.put(targetBlock, result.values().stream().mapToDouble(v -> v).min().orElse(0.0001d));
                }
            } else {
                result.put(targetBlock, result.values().stream().mapToDouble(v -> v).min().orElse(0.0001d));
            }
        }

        return result;
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
            throw new ElectrumServerRpcException(e.getMessage(), e);
        }
    }

    @Override
    public long getIdCounterValue() {
        return idCounter.get();
    }
}
