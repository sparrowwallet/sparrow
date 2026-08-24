package com.sparrowwallet.sparrow.net;

import com.github.arteam.simplejsonrpc.client.Transport;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcBatchException;
import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.sparrowwallet.drongo.protocol.HeaderChainState;
import com.sparrowwallet.drongo.protocol.VerificationException;
import com.sparrowwallet.drongo.wallet.BlockTransactionHash;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ElectrumServerRpc {
    /** The JSON-RPC standard code for a method the server does not implement, which bitcoind returns as well as Electrum servers. */
    int METHOD_NOT_FOUND = -32601;

    void ping(Transport transport);

    List<String> getServerVersion(Transport transport, String clientName, String[] supportedVersions);

    ServerFeatures getServerFeatures(Transport transport);

    String getServerBanner(Transport transport);

    BlockHeaderTip subscribeBlockHeaders(Transport transport);

    Map<String, ScriptHashTx[]> getScriptHashHistory(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError);

    Map<String, ScriptHashTx[]> getScriptHashMempool(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes, boolean failOnError);

    Map<String, String> subscribeScriptHashes(Transport transport, Wallet wallet, Map<String, String> pathScriptHashes);

    Map<String, Boolean> unsubscribeScriptHashes(Transport transport, Set<String> scriptHashes);

    SilentPaymentsSubscription subscribeSilentPayments(Transport transport, Wallet wallet, String scanPrivKeyHex, String spendPubKeyHex, Object start, int[] labels);

    String unsubscribeSilentPayments(Transport transport, String scanPrivKeyHex, String spendPubKeyHex);

    Map<Integer, String> getBlockHeaders(Transport transport, Wallet wallet, Set<Integer> blockHeights);

    Map<Integer, BlockStats> getBlockStats(Transport transport, Set<Integer> blockHeights);

    Map<String, String> getTransactions(Transport transport, Wallet wallet, Set<String> txids);

    Map<String, VerboseTransaction> getVerboseTransactions(Transport transport, Set<String> txids, String scriptHash);

    /**
     * Retrieves the merkle inclusion proof for each of the given transactions at the height it is claimed to be confirmed at.
     * The result is keyed by the exact pair as "txid:height", since the same transaction may legitimately be requested at two heights in one call,
     * and carries TransactionMerkleProof.ERROR_PROOF for every pair the server returned an error for.
     */
    Map<String, TransactionMerkleProof> getTransactionMerkleProofs(Transport transport, Wallet wallet, Collection<BlockTransactionHash> references);

    /**
     * Retrieves a run of consecutive block headers, throwing VerificationException if the response is malformed or short of the server's own tip.
     */
    BlockHeaders getBlockHeadersChunk(Transport transport, int startHeight, int count);

    /**
     * Retrieves several runs of consecutive block headers, keyed by start height. A range the server errors on, or whose response fails the
     * checks getBlockHeadersChunk applies, is omitted from the result rather than failing the others.
     */
    Map<Integer, BlockHeaders> getBlockHeadersChunks(Transport transport, Map<Integer, Integer> startHeightCounts);

    Map<Integer, Double> getFeeEstimates(Transport transport, List<Integer> targetBlocks);

    Map<Double, Long> getFeeRateHistogram(Transport transport);

    Double getMinimumRelayFee(Transport transport);

    String broadcastTransaction(Transport transport, String txHex);

    long getIdCounterValue();

    /** Whether every error in a batch reports the method as not found, which is a property of the server rather than of any one request. */
    static boolean isMethodNotFound(JsonRpcBatchException e) {
        return !e.getErrors().isEmpty() && e.getErrors().values().stream().allMatch(error -> error.getCode() == METHOD_NOT_FOUND);
    }

    static boolean isMethodNotFound(JsonRpcException e) {
        return e.getErrorMessage() != null && e.getErrorMessage().getCode() == METHOD_NOT_FOUND;
    }

    /**
     * Applies Electrum's checks to a blockchain.block.headers response. A server returning at most a partial difficulty period cannot serve the
     * header sync at all, and a response shorter than requested is only explicable by the server having reached its own announced tip.
     */
    static BlockHeaders checkBlockHeaders(BlockHeaders blockHeaders, int startHeight, int count) throws VerificationException {
        return checkBlockHeaders(blockHeaders, startHeight, count, AppServices.getCurrentBlockHeight());
    }

    static BlockHeaders checkBlockHeaders(BlockHeaders blockHeaders, int startHeight, int count, Integer tip) throws VerificationException {
        if(blockHeaders == null || blockHeaders.hex == null || blockHeaders.count < 0) {
            throw new VerificationException("Malformed response to a request for " + count + " block headers from height " + startHeight);
        }
        if(blockHeaders.max < HeaderChainState.RETARGET_INTERVAL) {
            throw new VerificationException("Server returns at most " + blockHeaders.max + " block headers per request, too few to cover a difficulty period");
        }
        if(blockHeaders.count > count) {
            throw new VerificationException("Requested " + count + " block headers from height " + startHeight + " but the server returned " + blockHeaders.count);
        }
        if(blockHeaders.hex.length() != blockHeaders.count * BlockHeaders.HEADER_HEX_LENGTH) {
            throw new VerificationException("Response to a request for block headers from height " + startHeight + " contains " + blockHeaders.hex.length() / 2
                    + " bytes for " + blockHeaders.count + " headers");
        }

        if(blockHeaders.count < count && tip != null && startHeight + blockHeaders.count - 1 < tip) {
            throw new VerificationException("Requested " + count + " block headers from height " + startHeight + " but the server returned only " + blockHeaders.count
                    + " while announcing a tip at height " + tip);
        }

        return blockHeaders;
    }
}
