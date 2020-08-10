package com.sparrowwallet.sparrow.net;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.github.arteam.simplejsonrpc.client.JsonRpcClient;
import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.sparrowwallet.drongo.protocol.Transaction.DUST_RELAY_TX_FEE;

public class SimpleElectrumServerRpc implements ElectrumServerRpc {
    private static final Logger log = LoggerFactory.getLogger(SimpleElectrumServerRpc.class);

    @Override
    public void ping(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            client.createRequest().method("server.ping").id(1).executeNullable();
        } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
            throw new ElectrumServerRpcException("Error pinging server", e);
        }
    }

    @Override
    public List<String> getServerVersion(Transport transport, String clientName, String[] supportedVersions) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            //Using 1.4 as the version number as EPS tries to parse this number to a float
            return client.createRequest().returnAsList(String.class).method("server.version").id(1).params(clientName, "1.4").execute();
        } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
            throw new ElectrumServerRpcException("Error getting server version", e);
        }
    }

    @Override
    public String getServerBanner(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return client.createRequest().returnAs(String.class).method("server.banner").id(1).execute();
        } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
            throw new ElectrumServerRpcException("Error getting server banner", e);
        }
    }

    @Override
    public BlockHeaderTip subscribeBlockHeaders(Transport transport) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return client.createRequest().returnAs(BlockHeaderTip.class).method("blockchain.headers.subscribe").id(1).execute();
        } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
            throw new ElectrumServerRpcException("Error subscribing to block headers", e);
        }
    }

    @Override
    public Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Map<String, String> pathScriptHashes, boolean failOnError) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, ScriptHashTx[]> result = new LinkedHashMap<>();
        for(String path : pathScriptHashes.keySet()) {
            try {
                ScriptHashTx[] scriptHashTxes = client.createRequest().returnAs(ScriptHashTx[].class).method("blockchain.scripthash.get_history").id(path).params(pathScriptHashes.get(path)).execute();
                result.put(path, scriptHashTxes);
            } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
                if(failOnError) {
                    throw new ElectrumServerRpcException("Failed to retrieve reference for path: " + path, e);
                }

                result.put(path, new ScriptHashTx[] {ScriptHashTx.ERROR_TX});
            }
        }

        return result;
    }

    @Override
    public Map<String, ScriptHashTx[]> getScriptHashMempool(Transport transport, Map<String, String> pathScriptHashes, boolean failOnError) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, ScriptHashTx[]> result = new LinkedHashMap<>();
        for(String path : pathScriptHashes.keySet()) {
            try {
                ScriptHashTx[] scriptHashTxes = client.createRequest().returnAs(ScriptHashTx[].class).method("blockchain.scripthash.get_mempool").id(path).params(pathScriptHashes.get(path)).execute();
                result.put(path, scriptHashTxes);
            } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
                if(failOnError) {
                    throw new ElectrumServerRpcException("Failed to retrieve reference for path: " + path, e);
                }

                result.put(path, new ScriptHashTx[] {ScriptHashTx.ERROR_TX});
            }
        }

        return result;
    }

    @Override
    public Map<String, String> subscribeScriptHashes(Transport transport, Map<String, String> pathScriptHashes) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, String> result = new LinkedHashMap<>();
        for(String path : pathScriptHashes.keySet()) {
            try {
                client.createRequest().method("blockchain.scripthash.subscribe").id(path).params(pathScriptHashes.get(path)).executeNullable();
                result.put(path, "");
            } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
                //Even if we have some successes, failure to subscribe for all script hashes will result in outdated wallet view. Don't proceed.
                throw new ElectrumServerRpcException("Failed to retrieve reference for path: " + path, e);
            }
        }

        return result;
    }

    @Override
    public Map<Integer, String> getBlockHeaders(Transport transport, Set<Integer> blockHeights) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<Integer, String> result = new LinkedHashMap<>();
        for(Integer blockHeight : blockHeights) {
            try {
                String blockHeader = client.createRequest().returnAs(String.class).method("blockchain.block.header").id(blockHeight).params(blockHeight).execute();
                result.put(blockHeight, blockHeader);
            } catch(IllegalStateException | IllegalArgumentException e) {
                log.warn("Failed to retrieve block header for block height: " + blockHeight + " (" + e.getMessage() + ")");
            } catch(JsonRpcException e) {
                log.warn("Failed to retrieve block header for block height: " + blockHeight + " (" + e.getErrorMessage() + ")");
            }
        }

        return result;
    }

    @Override
    public Map<String, String> getTransactions(Transport transport, Set<String> txids) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, String> result = new LinkedHashMap<>();
        for(String txid : txids) {
            try {
                String rawTxHex = client.createRequest().returnAs(String.class).method("blockchain.transaction.get").id(txid).params(txid).execute();
                result.put(txid, rawTxHex);
            } catch(JsonRpcException | IllegalStateException | IllegalArgumentException e) {
                result.put(txid, Sha256Hash.ZERO_HASH.toString());
            }
        }

        return result;
    }

    @Override
    public Map<String, VerboseTransaction> getVerboseTransactions(Transport transport, Set<String> txids) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<String, VerboseTransaction> result = new LinkedHashMap<>();
        for(String txid : txids) {
            try {
                VerboseTransaction verboseTransaction = client.createRequest().returnAs(VerboseTransaction.class).method("blockchain.transaction.get").id(txid).params(txid, true).execute();
                result.put(txid, verboseTransaction);
            } catch(IllegalStateException | IllegalArgumentException e) {
                log.warn("Error retrieving transaction: " + txid + " (" + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()) + ")");
            } catch(JsonRpcException e) {
                log.warn("Error retrieving transaction: " + txid + " (" + e.getErrorMessage() + ")");
            }
        }

        return result;
    }

    @Override
    public Map<Integer, Double> getFeeEstimates(Transport transport, List<Integer> targetBlocks) {
        JsonRpcClient client = new JsonRpcClient(transport);

        Map<Integer, Double> result = new LinkedHashMap<>();
        for(Integer targetBlock : targetBlocks) {
            try {
                Double targetBlocksFeeRateBtcKb = client.createRequest().returnAs(Double.class).method("blockchain.estimatefee").id(targetBlock).params(targetBlock).execute();
                result.put(targetBlock, targetBlocksFeeRateBtcKb);
            } catch(IllegalStateException | IllegalArgumentException e) {
                log.warn("Failed to retrieve fee rate for target blocks: " + targetBlock + " (" + e.getMessage() + ")");
                result.put(targetBlock, DUST_RELAY_TX_FEE);
            } catch(JsonRpcException e) {
                throw new ElectrumServerRpcException("Failed to retrieve fee rate for target blocks: " + targetBlock, e);
            }
        }

        return result;
    }

    @Override
    public String broadcastTransaction(Transport transport, String txHex) {
        try {
            JsonRpcClient client = new JsonRpcClient(transport);
            return client.createRequest().returnAs(String.class).method("blockchain.transaction.broadcast").id(1).params(txHex).execute();
        } catch(IllegalStateException | IllegalArgumentException e) {
            throw new ElectrumServerRpcException(e.getMessage(), e);
        } catch(JsonRpcException e) {
            throw new ElectrumServerRpcException(e.getErrorMessage().getMessage(), e);
        }
    }
}
